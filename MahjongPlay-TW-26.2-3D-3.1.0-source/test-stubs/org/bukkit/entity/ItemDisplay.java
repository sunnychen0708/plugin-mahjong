package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface ItemDisplay extends Display {
  enum ItemDisplayTransform { NONE, THIRDPERSON_LEFTHAND, THIRDPERSON_RIGHTHAND, FIRSTPERSON_LEFTHAND, FIRSTPERSON_RIGHTHAND, HEAD, GUI, GROUND, FIXED }
  void setItemStack(ItemStack stack); void setItemDisplayTransform(ItemDisplayTransform transform);
}
