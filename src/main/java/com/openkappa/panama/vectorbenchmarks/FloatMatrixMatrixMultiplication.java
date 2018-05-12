package com.openkappa.panama.vectorbenchmarks;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.Shapes;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static com.openkappa.panama.vectorbenchmarks.Util.YMM_FLOAT;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector", "-XX:TypeProfileLevel=222", "-XX:-TieredCompilation"})
public class FloatMatrixMatrixMultiplication {


  @Param({"64", "512", "1024"})
  int size;

  private float[] left;
  private float[] right;
  private float[] result;

  @Setup(Level.Iteration)
  public void init() {
    this.left = newMatrix(size);
    this.right = newMatrix(size);
    this.result = newMatrix(size);
  }

  @Benchmark
  public void mmmPanama(Blackhole bh) {
    mmulPanama(size, left, right, result);
    bh.consume(result);
  }

  @Benchmark
  public void mmmAutoVectorised(Blackhole bh) {
    fastBuffered(size, left, right, result);
    bh.consume(result);
  }


  private static void mmulPanama(int n, float[] left, float[] right, float[] result) {
    int blockWidth = n >= 256 ? 512 : 256;
    int block_height = n >= 512 ? 8 : n >= 256 ? 16 : 32;
    for (int columnOffset = 0; columnOffset < n; columnOffset += blockWidth) {
      for (int rowOffset = 0; rowOffset < n; rowOffset += block_height) {
        for (int i = 0; i < n; ++i) {
          for (int j = columnOffset; j < columnOffset + blockWidth && j < n; j += 64) {
            FloatVector<Shapes.S256Bit> sum1 = YMM_FLOAT.fromArray(result, i * n + j);
            FloatVector<Shapes.S256Bit> sum2 = YMM_FLOAT.fromArray(result, i * n + j + 8);
            FloatVector<Shapes.S256Bit> sum3 = YMM_FLOAT.fromArray(result, i * n + j + 16);
            FloatVector<Shapes.S256Bit> sum4 = YMM_FLOAT.fromArray(result, i * n + j + 24);
            FloatVector<Shapes.S256Bit> sum5 = YMM_FLOAT.fromArray(result, i * n + j + 32);
            FloatVector<Shapes.S256Bit> sum6 = YMM_FLOAT.fromArray(result, i * n + j + 40);
            FloatVector<Shapes.S256Bit> sum7 = YMM_FLOAT.fromArray(result, i * n + j + 48);
            FloatVector<Shapes.S256Bit> sum8 = YMM_FLOAT.fromArray(result, i * n + j + 56);
            for (int k = rowOffset; k < rowOffset + block_height && k < n; ++k) {
              FloatVector<Shapes.S256Bit> multiplier = YMM_FLOAT.broadcast(left[i * n + k]);
              sum1 = sum1.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j)));
              sum2 = sum2.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 8)));
              sum3 = sum3.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 16)));
              sum4 = sum4.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 24)));
              sum5 = sum5.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 32)));
              sum6 = sum6.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 40)));
              sum7 = sum7.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 48)));
              sum8 = sum8.add(multiplier.mul(YMM_FLOAT.fromArray(right, k * n + j + 56)));
            }
            sum1.intoArray(result, i * n + j);
            sum2.intoArray(result, i * n + j + 8);
            sum3.intoArray(result, i * n + j + 16);
            sum4.intoArray(result, i * n + j + 24);
            sum5.intoArray(result, i * n + j + 32);
            sum6.intoArray(result, i * n + j + 40);
            sum7.intoArray(result, i * n + j + 48);
            sum8.intoArray(result, i * n + j + 56);
          }
        }
      }
    }
  }


  private static float[] newMatrix(int size) {
    float[] matrix = new float[size * size];
    for (int i = 0; i < matrix.length; ++i) {
      matrix[i] = ThreadLocalRandom.current().nextFloat();
    }
    return matrix;
  }

  public void fastBuffered(int n, float[] a, float[] b, float[] c) {
    float[] bBuffer = new float[n];
    float[] cBuffer = new float[n];
    int in = 0;
    for (int i = 0; i < n; ++i) {
      int kn = 0;
      for (int k = 0; k < n; ++k) {
        float aik = a[in + k];
        System.arraycopy(b, kn, bBuffer, 0, n);
        saxpy(n, aik, bBuffer, cBuffer);
        kn += n;
      }
      System.arraycopy(cBuffer, 0, c, in, n);
      Arrays.fill(cBuffer, 0f);
      in += n;
    }
  }


  private void saxpy(int n, float aik, float[] b, float[] c) {
    for (int i = 0; i < n; ++i) {
      c[i] = Math.fma(aik, b[i], c[i]);
    }
  }
}