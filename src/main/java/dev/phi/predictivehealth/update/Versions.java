package dev.phi.predictivehealth.update;

/**
 * Compares two version strings.
 *
 * <p>The numbers of the parts are compared one by one, the missing parts count
 * as zero, so {@code 2.0} is newer than {@code 1.9.9}. A leading {@code v} and
 * everything after a {@code +} or a {@code -} are ignored.
 */
public final class Versions {
	private Versions() {
	}

	public static boolean newer(String candidate, String current) {
		int[] a = parts(candidate);
		int[] b = parts(current);

		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0;
			int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return x > y;
			}
		}

		return false;
	}

	private static int[] parts(String version) {
		String core = version.replaceFirst("^[vV]", "").split("[+\\-]", 2)[0];
		String[] split = core.split("\\.");
		int[] out = new int[split.length];

		for (int i = 0; i < split.length; i++) {
			String digits = split[i].replaceAll("\\D.*", "");
			out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
		}

		return out;
	}
}
