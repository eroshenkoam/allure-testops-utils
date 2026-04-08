package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.SharedStep;
import io.github.eroshenkoam.allure.client.dto.SharedStepCreate;
import io.github.eroshenkoam.allure.client.dto.SharedStepUpdate;
import io.github.eroshenkoam.allure.client.dto.TestCaseAttachment;
import okhttp3.MultipartBody;
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

public interface SharedStepService {

    @GET("api/rs/sharedstep/{id}")
    Call<SharedStep> findOne(
            @Path("id") Long id
    );

    @GET("api/rs/sharedstep")
    Call<Page<SharedStep>> findAll(
            @Query("projectId") Long projectId,
            @Query("search") String search,
            @Query("archived") Boolean archived,
            @Query("page") int page,
            @Query("size") int size
    );

    @POST("api/rs/sharedstep")
    Call<SharedStep> createStep(
            @Body SharedStepCreate request
    );

    @PATCH("api/rs/sharedstep/{id}")
    Call<SharedStep> updateStep(
            @Path("id") Long id,
            @Body SharedStepUpdate request
    );

    @DELETE("api/rs/sharedstep/{id}")
    Call<SharedStep> deleteStep(
            @Path("id") Long id
    );

    /**
     * Upload attachment for specific test case.
     */
    @Multipart
    @POST("api/rs/sharedstep/attachment")
    Call<List<TestCaseAttachment>> addAttachment(@Query("sharedStepId") Long id,
                                                 @Part List<MultipartBody.Part> files);

}
