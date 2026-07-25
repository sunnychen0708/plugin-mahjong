package org.joml;
public class Matrix4f {
  public float yawRadians;
  public float pitchRadians;
  public float uniformScale = 1f;
  public Matrix4f(){}
  public Matrix4f rotateY(float a){yawRadians=a;return this;}
  public Matrix4f rotateX(float a){pitchRadians=a;return this;}
  public Matrix4f scale(float s){uniformScale=s;return this;}
}
