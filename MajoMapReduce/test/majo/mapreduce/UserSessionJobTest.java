package majo.mapreduce;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pr�ft die Klasse {@link UserSessionJob}.
 * 
 * @author joshua.kornfeld@bur-kg.de
 *
 */
public class UserSessionJobTest {

	/** Konstante f�r Benutzerfilter-Schl�ssel */
	public static final String MENULOG_FILTER_USERNAME = "menulog.filter.username";

	/** Konstante f�r Wartezeit-In-Minuten-Schl�ssel */
	public static final String MENULOG_MINUTES_MAX = "menulog.minutes.max";
	
	/** Konstante f�r Wartezeit-In-Minuten-Schl�ssel */
	public static final String MENULOG_SECONDS_MAX = "menulog.seconds.max";

	/** Konstante f�r Menuefilter-Schl�ssel */
	public static final String MENULOG_FILTER_MENUE = "menulog.filter.menu";
	
	@Test
	public void testFilterPrg() {
		final Object[][] data = new Object[][] {
				{"C:\\AUFTRAG\\AUFTRAG.EXE", Boolean.FALSE},
				{"C:\\AUFTRAG\\auftrag.exe", Boolean.FALSE},
				{"C:\\BUR\\GH\\AUF\\AU_BLI.EXE", Boolean.FALSE},
				{"R:\\XPRG\\VOLLNEU\\AU\\AUWIN952.EXE", Boolean.FALSE},
				{"R:\\XPRG\\VOLLNEU\\AU\\AUWINXYZ.EXE", Boolean.FALSE},
				{"R:\\XPRG\\VOLLNEU\\BUCHPRG\\BUCH.EXE", Boolean.FALSE},
				{"GH", Boolean.TRUE},
				{"KND_BUHA", Boolean.FALSE},
				{"LIEF_BUHA", Boolean.FALSE},
		};
		final UserSessionJob.UserValueMapper main = 
				new UserSessionJob.UserValueMapper(); 
		for (final Object[] x: data) {
			final String prg = (String) x[0];
			final boolean check = (boolean) x[1];
			final boolean result = main.acceptProgram(prg);
			System.out.println(prg + " --> " + result);
			if (check) {
				assertTrue(result);
			} else {
				assertFalse(result);
			}
		}
	}
	
}
