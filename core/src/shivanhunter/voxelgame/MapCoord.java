package shivanhunter.voxelgame;

/**
 * Represents a unique Coordinate in the world's Map. Overrides hashCode() and
 * equals() and implements Comparable<MapCoord> for use in Map's HashMap and
 * PriorityQueues.
 */
public class MapCoord implements Comparable<MapCoord>{
	public final int x, z;
	public float generationPriority;
	
	public MapCoord(int x, int z, float generationPriority) {
		this.x = x;
		this.z = z;
		this.generationPriority = generationPriority;
	}
	
	public float distance(MapCoord other) {
		return distance(other.x, other.z);
	}
	
	public float distance(float otherX, float otherZ) {
		float dx = x - otherX, dz = z - otherZ;
		return (float)Math.sqrt(dx*dx + dz*dz);
	}
	
	@Override public int hashCode() {
		//Szudzik's function, using x and z mapped from the integers to the natural numbers
		int a = (x >= 0 ? x*2 : -x*2 - 1);
		int b = (z >= 0 ? z*2 : -z*2 - 1);
		return a >= b ? a * a + a + b : a + b * b;
	}
	
	@Override public boolean equals(Object other) {
		if (other != null & other instanceof MapCoord) {
			MapCoord otherCoord = (MapCoord)other;
			return (otherCoord.x == x && otherCoord.z == z);
		}
		
		return false;
	}

	@Override public int compareTo(MapCoord o) {
		return (int)Math.signum(generationPriority - o.generationPriority);
	}
}
