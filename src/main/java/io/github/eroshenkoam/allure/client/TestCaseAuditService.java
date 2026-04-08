package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.TestCaseAuditEntry;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TestCaseAuditService {

    /**
     * Find test case by id.
     */
    @GET("api/rs/testcase/audit")
    Call<Page<TestCaseAuditEntry>> getTestCaseAudit(@Query("testCaseId") Long testCaseId,
                                                    @Query("page") int page,
                                                    @Query("size") int size);

}
