package shivanhunter.voxelgame;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/**
 * Map maintains a collection of world regions centered in an n*n area around
 * the player. It is the World's responsibility to inform the Map when the
 * player's position changes.
 * 
 * Regions are stored in a HashMap which maps region coordinates to generated
 * Regions. Region coordinates are stored using a private class, MapCoord,
 * which overrides HashCode and Equals so it can be used in HashMaps, and
 * implements Comparable<MapCoord> so it can be used in a PriorityQueue. 
 */
public class Map {
	// the coordinate for the Region the player is in
	private MapCoord playerRegion;
	
	// the square radius around the player's region in which to maintain the Map
	private final int range;
	
	// the seed Regions use for world generation
	private long seed;
	
	/*
	 * Regions are generated in separate threads. Since adding every coordinate
	 * to its own thread immediately is almost as bad as doing them all in the
	 * main thread, the number of worker threads is limited.
	 */
	private int activeThreads = 0;
	private static final int maxThreads = 2;
	
	/*
	 * A Region "r" is created in two passes: First, the raw block data is
	 * generated in Region's constructor. Then, when block data for all regions
	 * immediately surrounding r have been generated, r generates its mesh,
	 * which displays the Region in the world.
	 * 
	 * As Regions go through the process of being generated and meshed, they
	 * (or the MapCoord for their position, if the Region is not created yet)
	 * will be in one of several lists representing their state.
	 */
	
	/*
	 * First, the MapCoord for the region is entered into a PriorityQueue.
	 * Coordinates are prioritized based on distance from the player's Region,
	 * from nearest to farthest. This queue must be reordered every time the
	 * player's Region changes.
	 * 
	 * Typically, toCreate is a large collection consisting of many Regions
	 * outside of the area being generated, but this can be empty if the
	 * player stands still for long enough.
	 */
	private PriorityQueue<MapCoord> toCreate = new PriorityQueue<MapCoord>();
	
	/*
	 * Regions are created in another thread. When a MapCoord is given to a
	 * RegionCreator thread, it is removed from toCreate and added to this list.
	 * 
	 * RegionsInProgress should never have more than <activeThreads> members.
	 * It may have fewer.
	 */
	private Collection<MapCoord> regionsInProgress = new LinkedList<MapCoord>();
	
	/*
	 * RegionCreator threads add finished Regions to this queue. BlockingQueue
	 * is thread-safe and is useful for communicating between threads in a
	 * producer-consumer context.
	 * 
	 * newRegions should always have very few members as it is emptied (and its
	 * Regions are added to the Map) every update.
	 */
	private BlockingQueue<Region> newRegions = new LinkedBlockingQueue<Region>();
	
	/**
	 * Regions are added to the Map on the next update after being added to
	 * newRegions. Once a Region is in the Map, it can be collided with by
	 * retrieving it using Map.get(int x, int z), and every update it is either
	 * rendered, or if debug rendering is enabled, a debug indicator is
	 * rendered showing its position in toCreateMesh or waitingForNeighbors.
	 * 
	 * With a large rendering radius, Map can grow to a large (>1000) size. 
	 */
	private HashMap<MapCoord, Region> map = new HashMap<MapCoord, Region>();

	/**
	 * When a Region is added to the Map and removed from regionsInProgress, it
	 * is also immediately entered in waitingForNeighbors. Meshing cannot occur
	 * until all eight adjacent Regions also exist in the Map.
	 * 
	 * Typically, waitingForNeighbors forms a border around visible Regions. It
	 * can grow to a somewhat large (>200 members) size.
	 */
	private Collection<Region> waitingForNeighbors = new LinkedList<Region>();
	
	/**
	 * Regions are added to toCreateMesh as soon as they have a full set of
	 * neighbors. This queue must also be reordered every time the player's
	 * Region changes.
	 * 
	 * Since meshing tends to be faster then generating, toCreateMesh is
	 * usually very small. Regions are meshed and removed faster than they
	 * can be generated.
	 */
	private PriorityQueue<Region> toCreateMesh = new PriorityQueue<Region>();
	
	/**
	 * Regions are meshed in another thread. As with regionsInProgress, this
	 * list contains the Regions currently being worked on by a thread.
	 */
	private Collection<Region> meshesInProgress = new LinkedList<Region>();
	
	/**
	 * As with newRegions, this queue is used to indicate which Regions have
	 * newly-created meshes from another thread. This queue is also emptied
	 * every update. Regions are polled from this queue in the main thread and
	 * updated to finish creating its mesh.
	 */
	private BlockingQueue<Region> newMeshes = new LinkedBlockingQueue<Region>();
	
	private static final boolean RENDERDEBUG = false;
	private Model toCreateIndicator;
	
	/**
	 * Constructs a Map given a seed and a rendering range. The map will
	 * generate regions in a range*2+1 by range*2+1 area.
	 * 
	 * @param seed the seed to use when generating regions
	 * @param range the rendering radius
	 */
	public Map(long seed, int range) {
		this.seed = seed;
		this.range = range;
		this.playerRegion = new MapCoord(0, 0, 0);
		
		ModelBuilder builder = new ModelBuilder();
		toCreateIndicator = builder.createBox(8, 8, 8,
				new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1)),
				 Usage.Position | Usage.Normal);
	}
	
	/**
	 * sets the player's Region and updates both PriorityQueues to reflect the
	 * new priorities.
	 * 
	 * @param x the player's region coordinate (not block coordinate) in x
	 * @param z the player's region coordinate (not block coordinate) in z
	 */
	public void setPlayerRegion(int x, int z) {
		if (playerRegion.x == x && playerRegion.z == z) return;
		
		playerRegion = new MapCoord(x, z, 0);
		
		// since PriorityQueue has not method to reorder the heap, just take
		// everything out and stick it all back in
		
		Collection<MapCoord> tempToCreate = new LinkedList<MapCoord>();
		Collection<Region> tempToCreateMesh = new LinkedList<Region>();
		
		// update priority and add to temp lists
		for (MapCoord coord : toCreate) {
			coord.generationPriority = playerRegion.distance(coord.x, coord.z);
			tempToCreate.add(coord);
		}
		for (Region r : toCreateMesh) {
			tempToCreateMesh.add(r);
		}
		for (Region r : map.values()) {
			r.setGenerationPriority(playerRegion.distance(r.getX(), r.getZ()));
		}
		
		// clear PriorityQueues
		toCreate.clear();
		toCreateMesh.clear();
		
		// add back from temp lists
		for (MapCoord coord : tempToCreate) {
			toCreate.add(coord);
		}
		for (Region r : tempToCreateMesh) {
			toCreateMesh.add(r);
		}
		
		//forceGenerate(x, z);
	}
	
	/**
	 * Returns the region at the given region coordinates (not block
	 * coordinates).
	 * 
	 * @param x the region's x coordinate
	 * @param z the region's z coordinate
	 * @return the Region at the given coordinates or null
	 */
	public Region get(int x, int z) {
		// this MapCoord does not need a generationPriority, use -1
		return map.get(new MapCoord(x, z, -1));
	}
	
	/**
	 * Gets some debug info to display the sizes of Map's various queues and lists
	 * 
	 * @return a String containing the sizes of Map's various queues and lists
	 */
	public String getDebugInfo() {
		return "toCreate: " + toCreate.size() + "\n" + 
				"regionsInProgress: " + regionsInProgress.size() + "\n" +
				"newRegions: " + newRegions.size() + "\n" +
				"waitingForNeighbors: " + waitingForNeighbors.size() + "\n" +
				"toCreateMesh: " + toCreateMesh.size() + "\n" +
				"meshesInProgress: " + meshesInProgress.size() + "\n" +
				"newMeshes: " + newMeshes.size() + "\n" +
				"map: " + map.size() + "\n" +
				"threads: " + activeThreads + "/" + maxThreads + "\n";
	}
	
	/**
	 * Renders and updates this Map.
	 * 
	 * @param environment the LinGDX Environment for rendering
	 * @param batch the LibGDX ModelBatch for rendering
	 */
	public void render(Environment environment, ModelBatch batch) {
		// add any coordinates in range of the player to toCreate if they don't exist already
		for (int i = playerRegion.x - range; i <= playerRegion.x + range; ++i) {
			for (int j = playerRegion.z - range; j <= playerRegion.z + range; ++j) {
				MapCoord coords = new MapCoord(i, j, playerRegion.distance(i, j));
				if (!map.containsKey(coords) &&
						!toCreate.contains(coords) &&
						!regionsInProgress.contains(coords)) {
					toCreate.add(coords);
				}
			}
		}
		
		// accept any finished Regions from newRegions
		// remove from regionsInProgress, put in map and waitingForNeighbors
		Region newRegion;
		while ((newRegion = newRegions.poll()) != null) {
			MapCoord coord = new MapCoord(
					newRegion.getX(), newRegion.getZ(),
					playerRegion.distance(newRegion.getX(), newRegion.getZ()));
			map.put(coord, newRegion);
			regionsInProgress.remove(coord);
			waitingForNeighbors.add(newRegion);
		}
		
		// if any Regions in waitingForNeighbors have a full set of neighbors,
		// remove and add to toCreateMesh
		Collection<Region> regionsToRemove = new LinkedList<Region>();
		
		for (Region r : waitingForNeighbors) {
			if (getNeighborhood(r).isComplete()) {
				regionsToRemove.add(r);
				toCreateMesh.add(r);
			}
		}
		
		for (Region r : regionsToRemove) {
			waitingForNeighbors.remove(r);		
		}

		// start worker threads for generating or meshing Regions
		while (activeThreads < maxThreads) {
			if (startThread()) {
				activeThreads++;
			} else break;
		}
		
		// accept newly meshed Regions from meshesInProgress
		// load the finished mesh into the Region's Model and ModelInstance
		Region newMesh;
		while ((newMesh = newMeshes.poll()) != null) {
			meshesInProgress.remove(newMesh);
			newMesh.loadMesh();
		}
		
		// get rid of anything outside the range of the player
		pruneMap();
		
		// render the map
		for (Region r : map.values()) {
			// deco layer is only rendered within a certain distance
			boolean drawDeco = playerRegion.distance(r.getX(), r.getZ()) < range/3f;
			r.render(environment, batch, drawDeco);
		}
		
		// render debug indicators
		if (RENDERDEBUG) {
			renderDebug(environment, batch);
		}
	}
	
	/**
	 * Gets the neighborhood for a given Region, consisting of the eight
	 * adjacent Regions and the Region itself
	 * 
	 * @param r the Region around which to get the neighborhood
	 * @return the neighborhood for the given Region
	 */
	private Neighborhood getNeighborhood (Region r) {
		return new Neighborhood(
				r,
				map.get(new MapCoord(r.getX(), r.getZ()+1, -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ()+1, -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ(), -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX(), r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ(), -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ()+1, -1))
				);
	}
	
	/**
	 * Starts a worker thread for either generating or meshing a Region.
	 * Returns false if no new thread was created - in this case, there is no
	 * more work to do in other threads.
	 * 
	 * @return whether a new thread was started
	 */
	private boolean startThread() {
		// either generate or mesh a thread
		// first handle case where both options are available
		if (!toCreate.isEmpty() && !toCreateMesh.isEmpty()) {
			
			// get nearest (most important) regions for each task
			MapCoord toCreateCoord = toCreate.peek();
			Region toCreateMeshRegion = toCreateMesh.peek();
			
			// work on whichever region is nearest
			if (toCreateMeshRegion.generationPriority < toCreateCoord.generationPriority) {
				// meshing threads can fail to start if the Neighborhood is no
				// longer complete (if some Regions have been pruned from the
				// Map). Only return true if the thread successfully starts.
				if (startThread(toCreateMeshRegion)) {
					return true;
				}
			} else {
				startThread(toCreateCoord);
				return true;
			}
		}
		// both options are not available - one or both PriorityQueues are empty
		else if (!toCreate.isEmpty()) {
			startThread(toCreate.peek());
			return true;
		}
		else if (!toCreateMesh.isEmpty()) {
			return startThread(toCreateMesh.peek());
		}
		return false;
	}
	
	/**
	 * Starts a worker thread for generating a Region at a goven coordinate.
	 * 
	 * @param coords the coordinates at which to generate a Region
	 */
	private void startThread(MapCoord coords) {
		regionsInProgress.add(coords);
		toCreate.remove(coords);
		new RegionCreator(coords).start();
	}
	
	/**
	 * Tries to start a thread for meshing a given Region. Returns true if the
	 * thread was started; returns false if the Neighborhood is not complete.
	 * 
	 * @param r the Region for which to create a mesh
	 * @return whether the thread was successfully started
	 */
	private boolean startThread(Region r) {
		Neighborhood n = getNeighborhood(r);
		if (n.isComplete()) {
			meshesInProgress.add(r);
			new RegionMeshCreator(r, n).start();
			toCreateMesh.remove(r);
			return true;
		} else {
			waitingForNeighbors.add(r);
			toCreateMesh.remove(r);
			return false;
		}
	}
	
	/**
	 * Prunes any Regions and MapCoords from queues and the Map if they are
	 * not within range of the player.
	 */
	private void pruneMap() {
		Collection<Region> regionsToRemove = new LinkedList<Region>();
		Collection<MapCoord> coordsToRemove = new LinkedList<MapCoord>();
		
		for (Region r : map.values()) {
			if (r.getX() < playerRegion.x - range || r.getX() > playerRegion.x + range ||
					r.getZ() < playerRegion.z - range || r.getZ() > playerRegion.z + range) {
				regionsToRemove.add(r);
			}
		}
		
		for (MapCoord coord : toCreate) {
			if (coord.x < playerRegion.x - range || coord.x > playerRegion.x + range ||
				coord.z < playerRegion.z - range || coord.z > playerRegion.z + range) {
				coordsToRemove.add(coord);
			}
		}

		for (Region r : regionsToRemove) {
			map.remove(new MapCoord(r.getX(), r.getZ(), -1));
			waitingForNeighbors.remove(r);
			toCreateMesh.remove(r);
			r.dispose();
		}
		
		for (MapCoord coord : coordsToRemove) {
			toCreate.remove(coord);
		}
	}
	
	/**
	 * Renders debug indicators for meshes in toCreate, waitingForNeighbors
	 * and toCreateMesh.
	 * 
	 * @param environment the LinGDX Environment for rendering
	 * @param batch the LibGDX ModelBatch for rendering
	 */
	private void renderDebug(Environment environment, ModelBatch batch) {
		for (MapCoord coord : toCreate) {
			ModelInstance box = new ModelInstance(toCreateIndicator);
			// put box at the Region in world space
			box.transform.translate(
					coord.x*Region.WIDTH + Region.WIDTH/2,
					16,
					coord.z*Region.WIDTH + Region.WIDTH/2);
			// box is yellow to red to black, from high priority to low
			box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(
							8f/coord.generationPriority,
							2f/coord.generationPriority,
							0,
							1)));
			batch.render(box, environment);
		}
	
		for (Region r : waitingForNeighbors) {
			ModelInstance box = new ModelInstance(toCreateIndicator);
			// put box at the Region in world space
			box.transform.translate(
					r.getX()*Region.WIDTH + Region.WIDTH/2,
					16,
					r.getZ()*Region.WIDTH + Region.WIDTH/2);
			// box is light blue
			box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(0, 0.5f, 1, 1)));
			batch.render(box, environment);
		}
	
		for (Region r : toCreateMesh) {
			ModelInstance box = new ModelInstance(toCreateIndicator);
			// put box at the Region in world space
			box.transform.translate(
					r.getX()*Region.WIDTH + Region.WIDTH/2,
					16,
					r.getZ()*Region.WIDTH + Region.WIDTH/2);
			// box is bright teal
			box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(0, 1, 1, 1)));
			batch.render(box, environment);
		}
	}
	
	/**
	 * Creates a Region at a given location.
	 */
	private class RegionCreator extends Thread {
		private final MapCoord coords;
		
		public RegionCreator(MapCoord coords) {
			this.coords = coords;
		}
		
		public void run() {
			try {
				newRegions.put(new Region(coords.x, coords.z, seed, playerRegion.distance(coords)));
				activeThreads--;
			} catch (InterruptedException e) {
				System.err.println("Thread interrupted when creating a region at (" + coords.x + ", " + coords.z + ")");
			}
		}
	}
	
	/**
	 * Meshes a Region at a given location.
	 */
	private class RegionMeshCreator extends Thread {
		private final Region r;
		private final Neighborhood n;
		
		public RegionMeshCreator(Region r, Neighborhood n) {
			this.r = r;
			this.n = n;
		}
		
		public void run() {
			try {
				if (n.isComplete()) {
					r.createMesh(n);
					newMeshes.put(r);
					activeThreads--;
				}
			} catch (InterruptedException e) {
				System.err.println("Thread interrupted when meshing a region at (" + r.getX() + ", " + r.getZ() + ")");
			}
		}
	}
}


