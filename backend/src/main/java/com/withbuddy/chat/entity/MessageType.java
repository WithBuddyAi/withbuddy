package com.withbuddy.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    user_question("user_question"),
    rag_answer("rag_answer"),
    no_result("no_result"),
    out_of_scope("out_of_scope"),
    suggestion("suggestion");

    private final String value;
}
