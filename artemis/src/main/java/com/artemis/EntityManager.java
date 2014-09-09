package com.artemis;

import java.util.BitSet;

import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;


/**
 * EntityManager.
 *
 * @author Arni Arent
 */
public class EntityManager extends Manager {

	/** Contains all entities in the manager. */
	private final Bag<Entity> entities;
	/** Stores the bits of all currently disabled entities IDs. */
	private final BitSet disabled;
	/** Amount of currently active (added to the world) entities. */
	private int active;
	/** Amount of entities ever added to the manager. */
	private long added;
	/** Amount of entites ever created by the manager. */
	private long created;
	/** Amount of entities ever deleted from the manager. */
	private long deleted;
	private RecyclingEntityFactory recyclingEntityFactory;

	private final Bag<BitSet> componentBits;
	private final Bag<BitSet> systemBits;
	
	CompoentIdentityResolver identityResolver = new CompoentIdentityResolver();
	private IntBag entityToIdentity = new IntBag();
	
	/**
	 * Creates a new EntityManager Instance.
	 */
	protected EntityManager(int initialContainerSize) {
		entities = new Bag<Entity>(initialContainerSize);
		disabled = new BitSet();
		
		componentBits = new Bag<BitSet>(initialContainerSize);
		systemBits = new Bag<BitSet>(initialContainerSize);
	}
	@Override
	protected void initialize() {
		recyclingEntityFactory = new RecyclingEntityFactory(world);
	}

	/**
	 * Create a new entity.
	 *
	 * @return a new entity
	 */
	protected Entity createEntityInstance() {
		Entity e = recyclingEntityFactory.obtain();
		componentBits(e).clear();
		systemBits(e).clear();
		created++;
		return e;
	}
	
	BitSet componentBits(Entity e) {
		return getBitSet(e, componentBits);
	}
	
	BitSet systemBits(Entity e) {
		return getBitSet(e, systemBits);
	}
	
	private static BitSet getBitSet(Entity e, Bag<BitSet> container) {
		BitSet bitset = container.safeGet(e.getId());
		if (bitset == null) {
			bitset = new BitSet();
			container.set(e.getId(), bitset);
		}
		
		return bitset;
	}
	
	/**
	 * Adds the entity to this manager.
	 * <p>
	 * Called by the world when an entity is added.
	 * </p>
	 *
	 * @param e
	 *			the entity to add
	 */
	@Override
	public void added(Entity e) {
		active++;
		added++;
		entities.set(e.getId(), e);
		
		entityToIdentity.set(e.getId(), identityResolver.getIdentity(e));
	}

	/**
	 * Sets the entity (re)enabled in the manager.
	 *
	 * @param e
	 *			the entity to (re)enable
	 */
	@Override
	public void enabled(Entity e) {
		disabled.clear(e.getId());
	}

	/**
	 * Sets the entity as disabled in the manager.
	 *
	 * @param e
	 *			the entity to disable
	 */
	@Override
	public void disabled(Entity e) {
		disabled.set(e.getId());
	}

	/**
	 * Removes the entity from the manager, freeing it's id for new entities.
	 *
	 * @param e
	 *			the entity to remove
	 */
	@Override
	public void deleted(Entity e) {
		entities.set(e.getId(), null);
		disabled.clear(e.getId());
		
		recyclingEntityFactory.free(e);
		
		active--;
		deleted++;
		
		entityToIdentity.set(e.getId(), 0);
	}

	/**
	 * Check if this entity is active.
	 * <p>
	 * Active means the entity is being actively processed.
	 * </p>
	 * 
	 * @param entityId
	 *			the entities id
	 *
	 * @return true if active, false if not
	 */
	public boolean isActive(int entityId) {
		return (entities.size() > entityId) ? entities.get(entityId) != null : false; 
	}
	
	/**
	 * Check if the specified entityId is enabled.
	 * 
	 * @param entityId
	 *			the entities id
	 *
	 * @return true if the entity is enabled, false if it is disabled
	 */
	public boolean isEnabled(int entityId) {
		return !disabled.get(entityId);
	}
	
	/**
	 * Get a entity with this id.
	 * 
	 * @param entityId
	 *			the entities id
	 *
	 * @return the entity
	 */
	protected Entity getEntity(int entityId) {
		return entities.get(entityId);
	}
	
	/**
	 * Get how many entities are active in this world.
	 *
	 * @return how many entities are currently active
	 */
	public int getActiveEntityCount() {
		return active;
	}
	
	/**
	 * Get how many entities have been created in the world since start.
	 * <p>
	 * Note: A created entity may not have been added to the world, thus
	 * created count is always equal or larger than added count.
	 * </p>
	 *
	 * @return how many entities have been created since start
	 */
	public long getTotalCreated() {
		return created;
	}
	
	/**
	 * Get how many entities have been added to the world since start.
	 *
	 * @return how many entities have been added
	 */
	public long getTotalAdded() {
		return added;
	}
	
	/**
	 * Get how many entities have been deleted from the world since start.
	 *
	 * @return how many entities have been deleted since start
	 */
	public long getTotalDeleted() {
		return deleted;
	}
	
	protected void clean() {
		recyclingEntityFactory.recycle();
	}
	
	protected int getIdentity(Entity e) {
		return identityResolver.getIdentity(e);
	}
	
	private static final class CompoentIdentityResolver {
		private final Bag<BitSet> composition;
		
		CompoentIdentityResolver() {
			composition = new Bag<BitSet>();
			composition.add(null);
		}
		
		int getIdentity(BitSet components) {
			Object[] bitsets = composition.getData();
			int size = composition.size();
			for (int i = 1; size > i; i++) { // want to start from 1 so that 0 can mean null
				if (components.equals(bitsets[i]))
					return i;
			}
			composition.add((BitSet)components.clone());
			return size;
		}
		
		int getIdentity(Entity e) {
			return getIdentity(e.getComponentBits());
		}
		
		BitSet getComponentBitSet(int id) {
			return composition.get(id);
		}
	}
	
	private static final class RecyclingEntityFactory {
		private final World world;
		private final WildBag<Entity> limbo;
		private final Bag<Entity> recycled;
		private int nextId;
		
		RecyclingEntityFactory(World world) {
			this.world = world;
			recycled = new Bag<Entity>();
			limbo = new WildBag<Entity>();
		}
		
		void free(Entity e) {
			limbo.add(e);
		}
		
		void recycle() {
			int s = limbo.size();
			if (s == 0) return;
			
			Object[] data = limbo.getData();
			for (int i = 0; s > i; i++) {
				Entity e = (Entity) data[i];
				recycled.add(e);
				data[i] = null;
			}
			limbo.setSize(0);
		}
		
		Entity obtain() {
			if (recycled.isEmpty()) {
				return new Entity(world, nextId++);
			} else {
				return recycled.removeLast();
			}
		}
	}
}
