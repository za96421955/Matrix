package com.matrix.service.service.agent.draco;

import com.matrix.common.util.JSONUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Evaluation 服务类
 *
 * @author 陈晨
 */
public class EvaluationService {

    public static void main(String[] args) {
        EvaluationService service = new EvaluationService();
        String question = "3";
        Evaluation evaluation = service.read("/Users/chenchen/Desktop/3-工作/Agent/matrix/draco_test/parquet/" + question + ".jsonl");
        System.out.println(evaluation.getId());
        System.out.println("资源目录：/Users/chenchen/Desktop/3-工作/Agent/matrix/draco_test/answer/" + question + "_source");
        System.out.println("直接完成以下任务");
        System.out.println("- 按以下评分标准，对 /Users/chenchen/Desktop/3-工作/Agent/matrix/draco_test/answer/" + question + "_answer.md 进行评分：");
        System.out.println("```");
        System.out.println(evaluation.getAnswer());
        System.out.println("```");
        System.out.println("---");
        service.calculateTotalWeight(evaluation);
    }

    public Evaluation read(String filePath) {
        try {
            String json = Files.readString(Path.of(filePath));
            return JSONUtil.parseObject(json, Evaluation.class);
        } catch (IOException e) {
            throw new RuntimeException("读取 Evaluation 文件失败, filePath=" + filePath, e);
        }
    }

    public int calculateTotalWeight(Evaluation evaluation) {
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

}


