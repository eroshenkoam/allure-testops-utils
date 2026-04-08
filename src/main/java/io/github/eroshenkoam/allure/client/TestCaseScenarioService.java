package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.ScenarioNormalized;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepCreate;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepUpdate;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepResponse;
import io.github.eroshenkoam.allure.client.dto.TestCaseAttachment;
import io.github.eroshenkoam.allure.client.dto.scenario.TestCaseScenarioV2;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface TestCaseScenarioService {

    /**
     * Get test case scenario.
     */
    @GET("api/rs/testcase/{id}/step")
    Call<ScenarioNormalized> getScenario(@Path("id") Long id);

    /**
     * Migrate test case scenario.
     */
    @POST("api/rs/testcase/{id}/migrate")
    Call<ResponseBody> migrateScenario(
            @Path("id") Long id
    );

    /**
     * Delete test case scenario.
     */
    @DELETE("api/rs/testcase/{id}/step")
    Call<Void> deleteScenario(@Path("id") Long id);

    /**
     * Set test cases scenario.
     */
    @POST("api/rs/testcase/{id}/scenario?v2=true")
    Call<Void> setScenario(@Path("id") Long id, @Body TestCaseScenarioV2 scenario);

    /**
     * Create scenario step.
     */
    @POST("api/rs/testcase/step")
    Call<ScenarioStepResponse> createStep(
            @Body ScenarioStepCreate step,
            @Query("beforeId") Long beforeId,
            @Query("afterId") Long afterId
    );

    /**
     * Create scenario step.
     */
    @POST("api/rs/testcase/step")
    Call<ScenarioStepResponse> createStep(
            @Body ScenarioStepCreate step,
            @Query("beforeId") Long beforeId,
            @Query("afterId") Long afterId,
            @Query("withExpectedResult") boolean withExpectedResult
    );

    /**
     * Update scenario step.
     */
    @PATCH("api/rs/testcase/step/{id}")
    Call<ScenarioNormalized> updateStep(
            @Path("id") Long id,
            @Body ScenarioStepUpdate patch
    );

    /**
     * Update scenario step.
     */
    @PATCH("api/rs/testcase/step/{id}")
    Call<ScenarioNormalized> updateStep(
            @Path("id") Long id,
            @Body ScenarioStepUpdate patch,
            @Query("withExpectedResult") boolean withExpectedResult
    );

    /**
     * Delete scenario step.
     */
    @DELETE("api/rs/testcase/step/{id}")
    Call<ScenarioNormalized> deleteStep(
            @Path("id") Long id
    );

    /**
     * Get test case attachments.
     */
    @GET("api/rs/testcase/attachment")
    Call<Page<TestCaseAttachment>> getAttachments(
            @Query("testCaseId") Long testCaseId,
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("/api/rs/testcase/attachment/{id}/content")
    Call<ResponseBody> getAttachmentContent(
            @Path("id") Long id
    );

    /**
     * Upload attachment for specific test case.
     */
    @Multipart
    @POST("api/rs/testcase/attachment")
    Call<List<TestCaseAttachment>> createAttachment(
            @Query("testCaseId") Long testCaseId,
            @Part List<MultipartBody.Part> files
    );

    @DELETE("api/rs/testcase/attachment/{id}")
    Call<Void> deleteAttachment(@Path("id") Long id);

}
