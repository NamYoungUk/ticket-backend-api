package com.sk.bds.ticket.api.data.model;

public enum StringMatchType {
    mismatch,
    equal,
    equalIgnoreCase,
    equalWithoutSpace,
    equalsIgnoreCaseWithoutSpace;

    public boolean isMatched() {
        return this != mismatch;
    }

    public boolean isEqual() {
        return this == equal;
    }

    public String text() {
        switch (this) {
            case equal:
                return "일치";
            case equalIgnoreCase:
                return "대소문자 불일치";
            case equalWithoutSpace:
                return "공백 불일치";
            case equalsIgnoreCaseWithoutSpace:
                return "대소문자, 공백 불일치";
        }
        return "불일치";
    }
}
