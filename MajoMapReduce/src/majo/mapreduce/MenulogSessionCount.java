package majo.mapreduce;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MenulogSessionCount {
	
	public static enum COUNTER {
		  OTHER_PROGRAM_LINE,
		  OTHER_USERNAME_LINE,
		  OTHER_DATETIME_LINE,
		  MATCH_LINE
	};

	public static class UserValueMapper 
	extends Mapper<Object, Text, Text, UserSession> {

		/** Programmfilter */
		public static final String FILTER_PRG = ".*\\\\AU(FTRAG)?(_BLI)?(WIN[\\d]+)?\\.EXE$";
		
		@Override
		public void map(
				final Object key, final Text value, final Context context) 
						throws IOException, InterruptedException {
			final MenulogLine line = new MenulogLine(value.toString());
			final String prg = line.getProgram().getName();
			if (acceptProgram(prg)) {
				final String username = line.getUser();
				if ("21".equals(username)) {
					// Filter: /input/150203.CSV
					final Calendar cal = line.getDateTime();
					if (2015 == cal.get(Calendar.YEAR) 
							&& Calendar.FEBRUARY == cal.get(Calendar.MONTH)
							&& 3 == cal.get(Calendar.DAY_OF_MONTH)) {
						final long timestamp = cal.getTimeInMillis();
						final String menue = line.getValue();
						final UserSession session = 
								new UserSession(username, timestamp, menue);
						context.write(new Text(username), session);
					} else {
						context.getCounter(COUNTER.OTHER_DATETIME_LINE).increment(1);					
					}
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

	public static class SessionReducer 
	extends Reducer<Text, UserSession, Text, SessionValues> {

		/** die Startseiten */
		private static final Set<String> STARTPAGES = new HashSet<>();
		
		static {
			STARTPAGES.add("2. Auskunft");
		}
		
		/** 
		 * maximaler Abstand zwischen zwei Eintr�gen in der Session:
		 * 10 Minuten * 60 Sekunden * 1000 Millisekunden 
		 */
		private static final long MAX = (10 * 60 * 1000);
		
		@Override
		public void reduce(
				final Text key, final Iterable<UserSession> values,
				final Context context) 
						throws IOException, InterruptedException {
			
			SessionValues lastUserSession = null;
			
			// alle Men�eintr�ge von Benutzer XYZ
			for (UserSession x: values) {

				final Map<Long, String> menues = x.getMenues();
				
				for (Long time: menues.keySet()) {
					final String menue = menues.get(time);					
					if (null == lastUserSession 
							|| (!checkMinMaxTime(lastUserSession, time))) {
						lastUserSession = new SessionValues();		
						context.write(key, lastUserSession);
					} else {
						lastUserSession.put(new LongWritable(time), new Text(menue));
					}
					
				}
			}			
		}
		
		/**
		 * F�gt der Sitzung einen Eintrag hinzu.
		 * @param time der Zeitpunkt
		 * @param menue der Men�eintrag
		 * @throws NullPointerException wenn {@code menue} gleich <code>null</code>
		 */
		public boolean checkMinMaxTime(final SessionValues session, final Long time) {
			boolean success = false;
			final SortedSet<Long> keys = new TreeSet<>();
			for (Writable x: session.keySet()) {
				LongWritable longWritable = (LongWritable) x;
				keys.add(Long.valueOf(longWritable.get()));
			}
			final long first = keys.first() - MAX;
			final long last = keys.last() + MAX;
			if (first <= time.longValue() && time.longValue() <= last) {
				success = true;
			}
			return success;
		}

	}
	
	public static void main(final String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "session count");
		job.setJarByClass(MenulogSessionCount.class);
		job.setMapperClass(UserValueMapper.class);
//		job.setCombinerClass(SessionReducer.class);
		job.setReducerClass(SessionReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(SessionValues.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(UserSession.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

	public static class SessionValues extends MapWritable {
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			Set<Writable> keys = this.keySet();
			for (Writable i: keys) {
				sb.append("{");
				sb.append(i.toString());
				sb.append(":");
				sb.append(this.get(i).toString());
				sb.append("}");
			}
			return sb.toString();
		}
		
	}

}