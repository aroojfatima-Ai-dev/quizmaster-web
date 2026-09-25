package com.quizmaster.web;

/** Names of the attributes kept in the HTTP session. */
public final class SessionKeys {

    /** The signed-in user's id. */
    public static final String USER_ID = "quizmaster.userId";
    /** The signed-in user's role, used to keep teachers out of student screens and vice versa. */
    public static final String USER_ROLE = "quizmaster.userRole";
    /** The in-progress test attempt (timer, answers). */
    public static final String ATTEMPT = "quizmaster.attempt";
    /** The test wizard's step-1 details and the manual questions collected so far. */
    public static final String WIZARD = "quizmaster.wizard";
    /** The imported questions awaiting "Confirm & Save All". */
    public static final String IMPORT_DRAFT = "quizmaster.importDraft";
    /** The graded attempt, kept only so the review screen can render it. */
    public static final String REVIEW = "quizmaster.review";

    private SessionKeys() {
    }
}
