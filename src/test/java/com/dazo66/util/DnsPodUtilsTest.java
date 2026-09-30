package com.dazo66.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DnsPodUtilsTest {

    @Test
    void acceptsSuccessfulRecordListWithoutTreatingItAsAnError() {
        assertThat(DnsPodUtils.parseSuccessfulResponse(
                "{\"status\":{\"code\":\"1\"},\"records\":[{\"id\":\"42\"}]}",
                "Record.List").getJSONArray("records").getJSONObject(0).getString("id"))
                .isEqualTo("42");
    }

    @Test
    void rejectsFailedOrMalformedResponses() {
        assertThat(DnsPodUtils.parseSuccessfulResponse(
                "{\"status\":{\"code\":\"10\"}}", "Record.List")).isNull();
        assertThat(DnsPodUtils.parseSuccessfulResponse("not json", "Record.Modify")).isNull();
    }
}
