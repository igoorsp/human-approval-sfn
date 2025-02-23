package com.example.sfn.lambda.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class SqsMessage {

    private String taskToken;
    private String pedidoId;
    private String usuarioEmail;
}
