package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.ScenarioNormalized;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepCreate;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepResponse;
import io.github.eroshenkoam.allure.client.dto.ScenarioStepUpdate;
import io.github.eroshenkoam.allure.client.dto.SharedStepAttachment;
import io.github.eroshenkoam.allure.client.dto.scenario.SharedStepScenario;
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

public interface SharedStepScenarioService {

    /**
     * Get test case scenario.
     */
    @GET("api/rs/sharedstep/{id}/step")
    Call<ScenarioNormalized> getScenario(@Path("id") Long id);

    /**
     * Delete test case scenario.
     */
    @DELETE("api/rs/sharedstep/{id}/step")
    Call<Void> deleteScenario(@Path("id") Long id);

    /**
     * Set test cases scenario.
     */
    @POST("api/rs/sharedstep/{id}/scenario")
    Call<Void> setScenario(@Path("id") Long id, @Body SharedStepScenario scenario);

    /**
     * Create scenario step.
     */
    @POST("api/rs/sharedstep/step")
    Call<ScenarioStepResponse> createStep(
            @Body ScenarioStepCreate step,
            @Query("beforeId") Long beforeId,
            @Query("afterId") Long afterId
    );

    /**
     * Create scenario step.
     */
    @POST("api/rs/sharedstep/step")
    Call<ScenarioStepResponse> createStep(
            @Body ScenarioStepCreate step,
            @Query("beforeId") Long beforeId,
            @Query("afterId") Long afterId,
            @Query("withExpectedResult") boolean withExpectedResult
    );

    /**
     * Update scenario step.
     */
    @PATCH("api/rs/sharedstep/step/{id}")
    Call<ScenarioNormalized> updateStep(
            @Path("id") Long id,
            @Body ScenarioStepUpdate patch
    );

    /**
     * Update scenario step.
     */
    @PATCH("api/rs/sharedstep/step/{id}")
    Call<ScenarioNormalized> updateStep(
            @Path("id") Long id,
            @Body ScenarioStepUpdate patch,
            @Query("withExpectedResult") boolean withExpectedResult
    );

    /**
     * Delete scenario step.
     */
    @DELETE("api/rs/sharedstep/step/{id}")
    Call<ScenarioNormalized> deleteStep(
            @Path("id") Long id
    );

    /**
     * Get test case attachments.
     */
    @GET("api/rs/sharedstep/attachment")
    Call<Page<SharedStepAttachment>> getAttachments(
            @Query("sharedStepId") Long sharedStepId,
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("/api/rs/sharedstep/attachment/{id}/content")
    Call<ResponseBody> getAttachmentContent(
            @Path("id") Long id
    );

    /**
     * Upload attachment for specific test case.
     */
    @Multipart
    @POST("api/rs/sharedstep/attachment")
    Call<List<SharedStepAttachment>> createAttachment(
            @Query("sharedStepId") Long sharedStepId,
            @Part List<MultipartBody.Part> files
    );

    @DELETE("api/rs/sharedstep/attachment/{id}")
    Call<Void> deleteAttachment(@Path("id") Long id);

}
