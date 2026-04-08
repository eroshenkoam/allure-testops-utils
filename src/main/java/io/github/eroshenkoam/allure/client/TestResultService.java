package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.*;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

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
     * Get test cases scenario.
     */
    @GET("api/rs/testresult/{id}/execution")
    Call<TestResultScenario> getScenario(@Path("id") Long id);

    @GET("/api/rs/testresult/attachment/{id}/content")
    Call<ResponseBody> getAttachmentContent(@Path("id") Long id);


}
