package org.svenehrke.triptychdemo.cross.replenishment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * How the DC shares its stock of a product among the requests waiting for it: in proportion to what each still needs,
 * so a shortfall hits every location alike instead of whoever happened to ask last.
 */
public final class FairShare {

	private FairShare() {}

	/**
	 * Splits {@code stock} among {@code outstanding} (oldest request first): everyone in full if the stock suffices,
	 * else proportionally by the largest-remainder method - {@code ⌊stock·o/Σo⌋} each, then the units left over one by
	 * one to the largest remainders, ties to the older request. Hands out exactly {@code min(stock, Σo)}, and never more
	 * than a request's outstanding quantity.
	 */
	public static List<Integer> allocate(int stock, List<Integer> outstanding) {
		long total = outstanding.stream().mapToLong(Integer::longValue).sum();
		if (stock >= total) return List.copyOf(outstanding);
		var shares = new ArrayList<Integer>(outstanding.size());
		var remainders = new long[outstanding.size()];
		int handedOut = 0;
		for (int i = 0; i < outstanding.size(); i++) {
			long numerator = (long) stock * outstanding.get(i);
			shares.add((int) (numerator / total));
			remainders[i] = numerator % total;
			handedOut += shares.get(i);
		}
		IntStream.range(0, outstanding.size()).boxed()
			.sorted(Comparator.<Integer>comparingLong(i -> remainders[i]).reversed().thenComparing(i -> i))
			.limit(stock - handedOut)
			.forEach(i -> shares.set(i, shares.get(i) + 1));
		return List.copyOf(shares);
	}
}
