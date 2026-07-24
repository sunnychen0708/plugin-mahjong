package org.bukkit.block;
import org.bukkit.Material;
import org.bukkit.World;
public interface Block {
  World getWorld(); int getX(); int getY(); int getZ(); Material getType(); void setType(Material material); void setType(Material material, boolean applyPhysics);
}
