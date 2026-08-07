package com.matrix.service.service.agent.draco;

import com.matrix.common.dto.model.Message;
import com.matrix.common.util.JSONUtil;
import com.matrix.service.service.agent.ModelService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation 服务类
 *
 * @author 陈晨
 */
public class EvaluationService {
    
    private static final String BASE_PATH = "/Users/chenchen/Desktop/3-工作/== AI ==/git/Matrix-Draco";

    public static void main(String[] args) throws IOException {
        EvaluationService service = new EvaluationService();
        String question = "13";
//        service.outputProblem(question);
//        service.outputScore(question);
        service.calculateAndWriteScore(question);

//        for (int i = 9; i <= 11; i++) {
//            service.calculateAndWriteScore(i + "");
//        }
    }

    private void outputProblem(String question) {
        Evaluation evaluation = this.read(BASE_PATH + "/parquet/" + question + ".jsonl");
//        System.out.println("\n---");
//        System.out.println("## Answer (Flash):");
        System.out.println("完成以下任务");
        System.out.println("- 结果输出至：" + BASE_PATH + "/answer/" + question + "_answer.md");
        System.out.println("- 源数据或临时文件存放：" + BASE_PATH + "/answer/" + question + "_source");
        System.out.println("- 互联网带格式的源文件，首先清洗后再使用");
        System.out.println("---");
        System.out.println(evaluation.getProblem());

//        System.out.println("\n---");
//        System.out.println("## Score (Pro):");
//        System.out.println("完成以下任务");
//        System.out.println("- 按以下评分标准，对 " + BASE_PATH + "/answer/" + question + "_answer.md 进行评分");
//        System.out.println("- 信息本地优先，缺失时联网补充：" + BASE_PATH + "/answer/" + question + "_source");
//        System.out.println("```");
//        System.out.println(evaluation.getAnswer());
//        System.out.println("```");
//        System.out.println("---");
//        this.calculateTotalWeight(evaluation);
    }

    private void outputScore(String question) throws IOException {
        Evaluation evaluation = this.read(BASE_PATH + "/parquet/" + question + ".jsonl");
        String answer = Files.readString(Path.of(BASE_PATH + "/answer/" + question + "_answer.md"));
//        System.out.println("\n\n\n---");
//        System.out.println("## Web Score (Pro):");
        System.out.println("按以下评分标准，对<汇报结果>进行评分。计算得出总得分，及百分制得分：");
        System.out.println("```");
        System.out.println(evaluation.getAnswer());
        System.out.println("---");
        this.calculateTotalWeight(evaluation);
        System.out.println("```");
        System.out.println("\n## 汇报结果");
        System.out.println("```");
        System.out.println(answer);
        System.out.println("```");
    }

    private Evaluation read(String filePath) {
        try {
            String json = Files.readString(Path.of(filePath));
            return JSONUtil.parseObject(json, Evaluation.class);
        } catch (IOException e) {
            throw new RuntimeException("读取 Evaluation 文件失败, filePath=" + filePath, e);
        }
    }

    private int calculateTotalWeight(Evaluation evaluation) {
        List<Evaluation.Section> sections = evaluation.getAnswer().getSections();
        int positiveTotal = 0;
        int negativeTotal = 0;
        for (Evaluation.Section section : sections) {
            int sectionPositiveTotal = 0;
            int sectionNegativeTotal = 0;
            for (Evaluation.Criterion criterion : section.getCriteria()) {
                if (criterion.getWeight() > 0) {
                    sectionPositiveTotal += criterion.getWeight();
                } else {
                    sectionNegativeTotal += criterion.getWeight();
                }
            }
            positiveTotal += sectionPositiveTotal;
            negativeTotal += sectionNegativeTotal;
            System.out.println(section.getTitle() + ": " + sectionPositiveTotal + ", " + sectionNegativeTotal);
        }
        System.out.println("总权重: " + positiveTotal + ", " + negativeTotal);
        return positiveTotal;
    }

    private ModelService modelService = new ModelService();
    private void calculateAndWriteScore(String question) throws IOException {
        Evaluation evaluation = this.read(BASE_PATH + "/parquet/" + question + ".jsonl");
        String answer = Files.readString(Path.of(BASE_PATH + "/answer/" + question + "_answer.md"));
        List<Evaluation.Section> sections = evaluation.getAnswer().getSections();
        double positiveTotal = 0;
        double negativeTotal = 0;
        int gain = 0;
        int lose = 0;
        StringBuilder output = new StringBuilder();
        for (Evaluation.Section section : sections) {
            int sectionPositiveTotal = 0;
            int sectionNegativeTotal = 0;
            for (Evaluation.Criterion criterion : section.getCriteria()) {
                String result = """
                    ## 汇报结果
                    ```
                    %s
                    ```
                    """.formatted(answer);
                String input = """
                    按以下<评分标准>，对<汇报结果>进行评分，直接输出分数
                    - 总分 > 0: 得分区间为 [0, 总分]，包含0
                    - 总分 < 0: 得分区间为 [总分, 0]，包含0
                    
                    ## 总分：%s
                    
                    ## 评分标准
                    ```
                    %s
                    ```
                    
                    直接输出分数（整数），不要任何解释：
                    """.formatted(criterion.getWeight(), criterion.getRequirement());
                int score = this.evaluation(result, input, 0);
                if (criterion.getWeight() > 0) {
                    sectionPositiveTotal += criterion.getWeight();
                    gain += score;
                } else {
                    sectionNegativeTotal += criterion.getWeight();
                    lose += lose;
                }
                String print = criterion.getRequirement() + ": " + score + "/" + criterion.getWeight();
                System.out.println(print);
                output.append(print).append("\n");
            }
            positiveTotal += sectionPositiveTotal;
            negativeTotal += sectionNegativeTotal;
            String print = section.getTitle() + ": " + sectionPositiveTotal + ", " + sectionNegativeTotal + "\n";
            System.out.println(print);
            output.append(print).append("\n");
        }
        String print = "总得分: " + gain + "/" + positiveTotal + ", " + lose + "/" + negativeTotal;
        System.out.println(print);
        output.append(print).append("\n");

        double score = gain + lose;
        score = (int) (score / (positiveTotal / 100) * 100) / 100D;
        print = "得分: " + score;
        System.out.println(print);
        output.append(print).append("\n");

        String filePath = BASE_PATH + "/answer/" + question + "_score.md";
        Path path = Paths.get(filePath);
        Files.write(path, output.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private int evaluation(String result, String input, int retry) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.user(result));
            messages.add(Message.user(input));
            String score = modelService.call(messages);
            return Integer.parseInt(score);
        } catch (Exception e) {
            return this.evaluation(result, input, ++retry);
        }
    }

}


