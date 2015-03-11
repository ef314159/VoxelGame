package shivanhunter.voxelgame;

public class Noise {
	/*
	 * Random noise function to generate landscape. Generates 2D noise given a
	 * coordinate pair and a seed using the FNV Hash algorithm:
	 * http://www.isthe.com/chongo/tech/comp/fnv/
	 * 
	 * Noise.get() generates the same noise value for the same coordinate/seed
	 * pair and there is no discernible pattern in the noise for small variations
	 * in coordinates.
	 * 
	 * Noise.get() is fast: a stress test on my machine took ~4.7 seconds to
	 * sum up the results of 4,294,967,296 calls to Noise.get(). It is safe to
	 * assume that Noise.get() will not be a bottleneck in performance-critical
	 * code, such as on-the-fly terrain generation.
	 * 
	 * @param x the X coordinate
	 * @param z the Z coordinate
	 * @param seed the seed (should remain constant)
	 * @return a pseudorandom float from 0.0 to 1.0
	 */
	public static float get(int x, int y, int z, long seed) {
		int FNV_hash = 0x811C9DC5;
		int FNV_prime = 0x1000193;
		
		FNV_hash ^= x;
		FNV_hash *= FNV_prime;
		
		FNV_hash ^= y;
		FNV_hash *= FNV_prime;
		
		FNV_hash ^= z;
		FNV_hash *= FNV_prime;
		
		FNV_hash ^= (int)seed;
		FNV_hash *= FNV_prime;
		
		FNV_hash ^= (int)seed >> 32;
		FNV_hash *= FNV_prime;
		
		final float inv_2_31 = 1.0f / 4294967296f;
		return .5f - FNV_hash*inv_2_31;
	}
}
