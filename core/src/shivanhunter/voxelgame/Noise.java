package shivanhunter.voxelgame;

public class Noise {
	private static final int
		FNV_PRIME = 0x1000193,
		FNV_OFFSETBASIS = 0x811C9DC5;
	
	private static final float
		INV_2_31 = 1.0f / 4294967296f;
	
	/**
	 * Random noise function to generate landscape. Generates 2D noise given a
	 * coordinate pair and a seed using the FNV Hash algorithm:
	 * http://www.isthe.com/chongo/tech/comp/fnv/
	 * 
	 * Noise.get() generates the same noise value for the same coordinate/seed
	 * pair and there is no discernible pattern in the noise for small variations
	 * in coordinates.
	 * 
	 * Noise.get() is fast: a stress test on my machine took ~17.6 seconds to
	 * sum up the results of 4,294,967,296 calls to Noise.get(). It is safe to
	 * assume that Noise.get() will not be a bottleneck in performance-critical
	 * code, such as on-the-fly terrain generation.
	 * 
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param z the Z coordinate
	 * @param seed the seed (should remain constant)
	 * @return a pseudorandom float from 0.0 to 1.0
	 */
	public static float get(int x, int y, int z, long seed) {
		int hash = FNV_OFFSETBASIS;
		hash = FNV_hash(hash, x);
		hash = FNV_hash(hash, y);
		hash = FNV_hash(hash, z);
		hash = FNV_hash(hash, (int)seed);
		hash = FNV_hash(hash, (int)seed >> 32);
		
		return .5f - hash*INV_2_31;
	}
	
	private static int FNV_hash(int hash, int value) {
		hash ^= (byte)value;
		hash *= FNV_PRIME;

		hash ^= (byte)value >> 8;
		hash *= FNV_PRIME;

		hash ^= (byte)value >> 16;
		hash *= FNV_PRIME;

		hash ^= (byte)value >> 24;
		hash *= FNV_PRIME;
		
		return hash;
	}
}

