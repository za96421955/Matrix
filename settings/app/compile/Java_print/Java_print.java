public class Java_print {
    public static void main(String[] args) {
        String json = args[0];
        // 简单正则提取，不依赖外部库
        String value = json.replaceAll(".*\"print\"\\s*:\\s*\"([^\"]*)\".*", "$1");
        System.out.println(value);
    }
}