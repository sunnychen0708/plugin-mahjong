package org.bukkit.entity;
import java.util.UUID;
import org.bukkit.Location;
public interface Entity {
  UUID getUniqueId(); void setPersistent(boolean value); void setVisibleByDefault(boolean value); void remove(); boolean teleport(Location location);
}
