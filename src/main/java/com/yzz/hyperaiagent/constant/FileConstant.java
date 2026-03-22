package com.yzz.hyperaiagent.constant;

/**
 * 这是接口，字段自动是 public static final的
 * 类的话需要 public static才能调用 字段必须显式声明为 public static才能像常量一样直接调用
 * 接口不能实例化，避免误创建对象
 *
 */
public interface FileConstant {

    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
//public class FileConstant {
//
//    public static String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
//}
