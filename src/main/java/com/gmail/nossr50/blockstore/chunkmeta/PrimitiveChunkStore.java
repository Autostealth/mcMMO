package com.gmail.nossr50.blockstore.chunkmeta;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.World;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.blockstore.old.ChunkletStore;

public class PrimitiveChunkStore implements ChunkStore {
    private static final long serialVersionUID = -1L;
    transient private boolean dirty = false;
    /** X, Z, Y */
    public boolean[][][] store;
    private static final int CURRENT_VERSION = 7;
    private static final int MAGIC_NUMBER = 0xEA5EDEBB;
    private int cx;
    private int cz;
    private UUID worldUid;
    private List<UUID> spawnedMobs = new ArrayList<UUID>();
    transient private int worldHeight;

    public PrimitiveChunkStore(World world, int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
        this.worldUid = world.getUID();
        this.worldHeight = world.getMaxHeight();
        this.store = new boolean[16][16][this.worldHeight];
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public int getChunkX() {
        return cx;
    }

    @Override
    public int getChunkZ() {
        return cz;
    }

    @Override
    public boolean isTrue(int x, int y, int z) {
        return store[x][z][y];
    }

    @Override
    public void setTrue(int x, int y, int z) {
        store[x][z][y] = true;
        dirty = true;
    }

    @Override
    public void setFalse(int x, int y, int z) {
        store[x][z][y] = false;
        dirty = true;
    }

    @Override
    public boolean isEmpty() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < this.worldHeight; y++) {
                    if (store[x][z][y]) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void copyFrom(ChunkletStore otherStore) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < this.worldHeight; y++) {
                    store[x][z][y] = otherStore.isTrue(x, y, z);
                }
            }
        }
        dirty = true;
    }

    @Override
    public boolean isSpawnedMob(UUID id) {
        return spawnedMobs.contains(id);
    }

    @Override
    public boolean isSpawnedPet(UUID id) {
        return spawnedMobs.contains(id);
    }

    @Override
    public void addSpawnedMob(UUID id) {
        if (!isSpawnedMob(id)) {
            spawnedMobs.add(id);
            dirty = true;
        }
    }

    @Override
    public void addSpawnedPet(UUID id) {
        if (!isSpawnedPet(id)) {
            spawnedMobs.add(id);
            dirty = true;
        }
    }

    @Override
    public void removeSpawnedMob(UUID id) {
        if (isSpawnedMob(id)) {
            spawnedMobs.remove(id);
            dirty = true;
        }
    }

    @Override
    public void removeSpawnedPet(UUID id) {
        if (isSpawnedPet(id)) {
            spawnedMobs.remove(id);
            dirty = true;
        }
    }

    @Override
    public void clearSpawnedMobs() {
        if (!spawnedMobs.isEmpty()) {
            spawnedMobs.clear();
            dirty = true;
        }
    }

    @Override
    public void clearSpawnedPets() {
        if (!spawnedMobs.isEmpty()) {
            spawnedMobs.clear();
            dirty = true;
        }
    }

    @Override
    public List<UUID> getSpawnedMobs() {
        return spawnedMobs;
    }

    @Override
    public List<UUID> getSpawnedPets() {
        return spawnedMobs;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(MAGIC_NUMBER);
        out.writeInt(CURRENT_VERSION);

        out.writeLong(worldUid.getLeastSignificantBits());
        out.writeLong(worldUid.getMostSignificantBits());
        out.writeInt(cx);
        out.writeInt(cz);
        out.writeObject(store);

        out.writeObject(spawnedMobs);

        dirty = false;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int fileVersionNumber; // Can be used to determine the format of the file

        long lsb = in.readLong();
        if (((int) (lsb >> 32)) == MAGIC_NUMBER) {
            fileVersionNumber = (int) lsb;
            lsb = in.readLong();
        } else {
            fileVersionNumber = 0;
        }

        long msb = in.readLong();
        worldUid = new UUID(msb, lsb);
        cx = in.readInt();
        cz = in.readInt();

        // Constructor is not invoked, need to set these fields
        World world = mcMMO.p.getServer().getWorld(this.worldUid);

        this.worldHeight = world.getMaxHeight();

        store = (boolean[][][]) in.readObject();

        if (fileVersionNumber < CURRENT_VERSION) {
            if (fileVersionNumber < 5)
                fixArray();
            if (fileVersionNumber < 6) {
                spawnedMobs = new ArrayList<UUID>();
            }
            dirty = true;
        }

        if (fileVersionNumber == 6) {
            //What do we want to do about this? These casts are unchecked.
            spawnedMobs = (ArrayList<UUID>) in.readObject();
            List<UUID> spawnedPets = (ArrayList<UUID>) in.readObject();
            spawnedMobs.addAll(spawnedPets);
        }

        if(fileVersionNumber >= 7) {
            spawnedMobs = (ArrayList<UUID>) in.readObject();
        }
    }

    private void fixArray() {
        boolean[][][] temp = this.store;
        this.store = new boolean[16][16][this.worldHeight];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < this.worldHeight; y++) {
                    try {
                        store[x][z][y] = temp[x][y][z];
                    }
                    catch(Exception e) {}
                }
            }
        }
    }
}