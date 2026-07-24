package org.bukkit.entity;
public interface Display extends Entity {
  enum Billboard { FIXED, VERTICAL, HORIZONTAL, CENTER }
  record Brightness(int blockLight,int skyLight) {}
  void setViewRange(float range); void setBrightness(Brightness brightness); void setBillboard(Billboard billboard);
}
