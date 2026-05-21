package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.*;
import io.github.eroshenkoam.allure.client.dto.scenario.TestResultScenarioV2;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@SuppressWarnings("PMD.LinguisticNaming")
public interface TestResultService {

    /**
     * Find test case by id.
     */
    @GET("api/rs/testresult/{id}")
    Call<TestResult> findById(@Path("id") Long id);

    /**
     * Find test cases in project by rql.
     */
    @GET("api/rs/testresult/__search")
    Call<Page<TestResult>> findByRql(
            @Query("projectId") Long projectId,
            @Query("rql") String rql,
            @Query("page") int page,
            @Query("size") int size
    );

    /**
     * Get test result scenario in legacy shape.
     */
    @GET("api/rs/testresult/{id}/execution")
    Call<TestResultScenario> getScenario(@Path("id") Long id);

    /**
     * Replace test result scenario with v2 (formatted) shape.
     * Backed by the migration-only hidden endpoint:
     *   POST /api/rs/testresult/{id}/scenario?v2  (TestResultScenarioMigrationController)
     */
    @POST("api/rs/testresult/{id}/scenario?v2")
    Call<Void> setScenario(@Path("id") Long id, @Body TestResultScenarioV2 scenario);

    @GET("/api/rs/testresult/attachment/{id}/content")
    Call<ResponseBody> getAttachmentContent(@Path("id") Long id);

    @GET("/api/rs/testcase/attachment/{id}/content")
    Call<ResponseBody> getTestCaseAttachmentContent(@Path("id") Long id);

    @GET("api/rs/testresult/attachment")
    Call<Page<TestResultAttachment>> getAttachments(
            @Query("testResultId") Long testResultId,
            @Query("page") int page,
            @Query("size") int size
    );

    @Multipart
    @POST("api/rs/testresult/attachment")
    Call<List<TestResultAttachment>> createAttachment(
            @Query("testResultId") Long testResultId,
            @Part List<MultipartBody.Part> files
    );

}
