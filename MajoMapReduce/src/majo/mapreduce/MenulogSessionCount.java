package majo.mapreduce;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Ananlyse der Benutzersitzungen.
 * 
 * @author majo
 *
 */
public class MenulogSessionCount {
	
	/**
	 * Beschreibung f�r verschiedene Z�hler im Job.
	 */
	public static enum COUNTER {
		  OTHER_PROGRAM_LINE,
		  OTHER_USERNAME_LINE,
		  OTHER_DATETIME_LINE,
		  MATCH_LINE
	}

	private static final String MENULOG_FILTER_USERNAME = "menulog.filter.username";
	
	private static final String MENULOG_MINUTES_MAX = "menulog.minutes.max";

	/**
	 * Konfiguration und Starter f�r den Job.
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		// Konfiguration
		Configuration conf = new Configuration();
		conf.set(MENULOG_FILTER_USERNAME, "22");
		conf.setInt(MENULOG_MINUTES_MAX, 30);

		// Job anlegen
		final Job job = Job.getInstance(conf, 
				MenulogSessionCount.class.getSimpleName());
		job.setJarByClass(MenulogSessionCount.class);
		
		// Mappper + Combiner + Reducer
		job.setMapperClass(UserValueMapper.class);
		job.setCombinerClass(SessionReducer.class);
		job.setReducerClass(SessionReducer.class);
		
		// Output
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(UserSession.class);
		
		// Mapper-Output
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(UserSession.class);
		
		// Input- und Output-Pfad
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
		// Ausf�hrung abwarten
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

	/**
	 * Interpretiert eine Menulog-Zeile. Wird der Programmname vom Filter 
	 * {@link UserValueMapper#FILTER_PRG} akzeptiert, wird ein Schl�ssel/Wert-
	 * Paar im Kontext abgelegt. Schl�ssel ist der Benutzername und ein Wert 
	 * ist ein Objekt {@link UserSession}. Letzteres nimmt den Benutzernamen 
	 * (zus�tzlich zum Schl�ssel), den Zeitpunkt und den Men�punkt auf.
	 */
	public static class UserValueMapper 
	extends Mapper<Object, Text, Text, UserSession> {

		/** der Programmfilter */
		public static final String FILTER_PRG = 
				".*\\\\AU(FTRAG)?(_BLI)?(WIN[\\d]+)?\\.EXE$";
		
		@Override
		public void map(
				final Object key, final Text value, final Context context) 
						throws IOException, InterruptedException {
			final Configuration conf = context.getConfiguration();
			final String filterUser = conf.get(MENULOG_FILTER_USERNAME);
			
			final MenulogLine line = new MenulogLine(value.toString());
			final String prg = line.getProgram().getName();
			if (acceptProgram(prg)) {
				final String username = line.getUser();
				if (null == filterUser || filterUser.equals(username)) {
					// Filter: /input/150203.CSV
					final Calendar cal = line.getDateTime();
//					if (2015 == cal.get(Calendar.YEAR) 
//							&& Calendar.FEBRUARY == cal.get(Calendar.MONTH)
//							&& 3 == cal.get(Calendar.DAY_OF_MONTH)) {
						final long timestamp = cal.getTimeInMillis();
						final String menue = line.getValue();
						final UserSession session = 
								new UserSession(username, timestamp, menue);
						context.write(new Text(username), session);
//					} else {
//						context.getCounter(COUNTER.OTHER_DATETIME_LINE).increment(1);					
//					}
				} else {
					context.getCounter(COUNTER.OTHER_USERNAME_LINE).increment(1);					
				}
			} else {
				context.getCounter(COUNTER.OTHER_PROGRAM_LINE).increment(1);
			}
		}
		
		/**
		 * Liefert <code>true</code> wenn {@code prg} einem gesuchten 
		 * Programmnamen entspricht.
		 * @param prg ein Programmname
		 * @return <code>true</code> wenn gesucht
		 */
		public boolean acceptProgram(final String prg) {
			boolean match = false;
			if (null != prg) {
				match = prg.toUpperCase().matches(FILTER_PRG);
			}
			return match;
		}
		
	}

	/**
	 * Reduziert die vom {@link UserValueMapper} gesammelten Werte pro Benutzer.
	 */
	public static class SessionReducer 
	extends Reducer<Text, UserSession, Text, UserSession> {

		/** die Startseiten */
		private static final Set<String> STARTPAGES = new HashSet<>();
		
		static {
			STARTPAGES.add("2. Auskunft");
		}
		
		/** 
		 * maximaler Abstand zwischen zwei Eintr�gen in der Session:
		 * x Minuten * 60 Sekunden * 1000 Millisekunden
		 * 30 Minuten = 1.800.000
		 */
		private static final long FACTOR = (60 * 1000);
		
		private long delay = (30 * FACTOR);
		
		@Override
		public void reduce(
				final Text key, final Iterable<UserSession> values,
				final Context context) 
						throws IOException, InterruptedException {
			// gesammelte Sitzungen
			final TreeSet<UserSession> sessions = new TreeSet<>();
			final TreeMap<Long,String> cache = new TreeMap<>();

			// Konfiguration
			final Configuration conf = context.getConfiguration();
			final int minutes = conf.getInt(MENULOG_MINUTES_MAX, 30);
			delay = (minutes * FACTOR);

			System.out.println("username = " + key.toString() + ", delay = " + delay);
			
			// Sitzungen ermitteln
			int count = 0;
			for (UserSession x: values) {
				final Map<Long, String> menues = x.getMenues();
				System.out.println("\tfirst = " + x.getFirstTime() + ", size = " + menues.size());
				for (Entry<Long, String> e: menues.entrySet()) {
					final long time = e.getKey().longValue();
					final String menue = e.getValue();
					if (cache.isEmpty()) {
						cache.put(Long.valueOf(time), menue);
						System.out.println("\t\tinit");
					} else {
						if (checkMinMaxTime(
								cache.firstKey().longValue(), cache.lastKey().longValue(), 
								time)) {
							cache.put(Long.valueOf(time), menue);
							System.out.println("\t\tcache: " + time + " >> " + cache.size());
						} else {
							final UserSession newSession = new UserSession();
							newSession.getMenues().putAll(cache);
							sessions.add(newSession);
							System.out.println("\t\tcreate: " + newSession.getFirstTime() + ", size = " + newSession.getMenues().size());
						}
					}
				}
				count++;
			}			
			
			
			// Sitzungen ver�ffentlichen 
			for (UserSession x: sessions) {
				context.write(key, x);
				System.out.println("\t\t\tpublish: " + x.getFirstTime() + " --> " + x.getMenues().size());
			}
			
			System.out.println(key.toString() + " (" + count + ") --> " + sessions.size());
			
//			sessions.clear();
			
		}
		
		/**
		 * Pr�ft den Zeitpunkt, ob dieser zur Sitzung passt.
		 * @param x eine Benutzersitzung
		 * @param time ein Zeitpunkt
		 */
		public boolean checkMinMaxTime(
				final long first, final long last, final long time) {
			boolean success = false;
			final long min = first - delay;
			final long max = last + delay;
			if (min <= time && time <= max) {
				success = true;
			}
			return success;
		}

	}
	
}