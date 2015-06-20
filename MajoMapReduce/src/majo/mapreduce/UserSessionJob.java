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
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * Ananlyse der Benutzersitzungen.
 * 
 * @author majo
 *
 */
public class UserSessionJob {
	
	/**
	 * Beschreibung f�r verschiedene Z�hler im Job.
	 */
	public static enum COUNTER {
		  OTHER_PROGRAM_LINE,
		  OTHER_USERNAME_LINE,
		  OTHER_DATETIME_LINE,
		  MATCH_LINE
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
				"(GH)|(KND_BUHA)|(LIEF_BUHA)";
		// Vor der Filterung
//		".*\\\\(FTRAG)?(_BLI)?(WIN[\\d]+)?\\.EXE$";
		
		@Override
		public void map(
				final Object key, final Text value, final Context context) 
						throws IOException, InterruptedException {
			final Configuration conf = context.getConfiguration();
			final String filterUser = conf.get(Main.MENULOG_FILTER_USERNAME);
			
			/**
			 * Mapper lesen im Standardfall CSV Dateien Zeilenweise ein,
			 * daher entspricht {@code value} einer Zeile im CSV und kann
			 * in das Objekt MenulogLine �bersetzt werden.
			 */
			final MenulogLine line = new MenulogLine(value.toString());
			
			
			final String prg = line.getCleanProgram();
			if (acceptProgram(prg)) {
				final String username = line.getUser();
				if (null == filterUser || filterUser.equals(username)) {
					// Filter: /input/150203.CSV
					final Calendar cal = line.getDateTime();
//					if (2015 == cal.get(Calendar.YEAR) 
//							&& Calendar.FEBRUARY == cal.get(Calendar.MONTH)
//							&& 3 == cal.get(Calendar.DAY_OF_MONTH)) {
						final long timestamp = cal.getTimeInMillis();
						final String menue = line.getCleanValue();
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

		/** eine Ausgabe zur Fehlersuche */
		private final Monitor monitor = new Monitor(false);
		
		/** die Startseiten */
		private static final Set<String> STARTPAGES = new HashSet<>();	
		static {
			STARTPAGES.add("2. Auskunft");
		}
		
		/** die Abschlussseiten */
		private static final Set<String> ENDPAGES = new HashSet<>();	
		static {
			ENDPAGES.add("Z. Programm beenden");
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
			final int minutes = conf.getInt(Main.MENULOG_MINUTES_MAX, 30);
			delay = (minutes * FACTOR);

			monitor.println("username = " + key.toString() + ", delay = " + delay);
			
			// Sitzungen ermitteln
			int count = 0;
			for (final UserSession x: values) {
				final Map<Long, String> menues = x.getMenues();
				monitor.println("\tmenues: first = " + x.getFirstTime() + ", size = " + menues.size());
				for (final Entry<Long, String> e: menues.entrySet()) {
					// aktuelle Wert: Zeit + Men�punkt
					final long time = e.getKey().longValue();
					final String menue = e.getValue();
					if (!ENDPAGES.contains(menue)) {
						if (cache.isEmpty()) {
							// neue Sitzung beginnen
							cache.put(Long.valueOf(time), menue);
							monitor.println("\t\tinit");
						} else {
							if ((!STARTPAGES.contains(menue)) && checkMinMaxTime(
									cache.firstKey().longValue(), 
									cache.lastKey().longValue(), 
									time)) {
								// geh�rt mit zur akt. Sitzung
								cache.put(Long.valueOf(time), menue);
								monitor.println("\t\tcache: " + time + " >> " + cache.size());
							} else {
								// akt. Sitzung �bernehmen (akt. Wert geh�rt nicht dazu)
								final UserSession newSession = new UserSession();
								newSession.getMenues().putAll(cache);
								sessions.add(newSession);
								monitor.println("\t\tcreate: " + newSession.getFirstTime() + ", size = " + newSession.getMenues().size());
								cache.clear();
								// neue Sitzung beginnen (mit Wert, der nicht dazu geh�rt)
								cache.put(Long.valueOf(time), menue);
							}
						}
					}
				}
				count++;
			}			
			
			
			// Sitzungen ver�ffentlichen 
			for (final UserSession x: sessions) {
				context.write(key, x);
				monitor.println("\t\t\tpublish: " + x.getFirstTime() + " --> " + x.getMenues().size());
			}
			
			monitor.println(key.toString() + " (" + count + ") --> " + sessions.size());
			
			sessions.clear();
			
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
			monitor.println("\t\tcheck: " + min + " / " + time + " / " + max + " = " + success);
			return success;
		}

	}
	
}