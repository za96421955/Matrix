package com.matrix.common.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequest extends PatternRequest {
    @Serial
    private static final long serialVersionUID = 4709547876160097494L;

    private String skillName;

    @Override
    public String toString() {
        return super.toString();
    }

}


