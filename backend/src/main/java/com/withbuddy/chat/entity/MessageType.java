package com.withbuddy.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    USER_QUESTION("user_question"),
    RAG_ANSWER("rag_answer"),
    NO_RESULT("no_result"),
    OUT_OF_SCOPE("out_of_scope"),
    SUGGESTION("suggestion");

    private final String value;
}
