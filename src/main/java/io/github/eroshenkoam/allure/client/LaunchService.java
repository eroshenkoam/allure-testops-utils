package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.CreateLaunchEvent;
import io.github.eroshenkoam.allure.client.dto.Launch;
import io.github.eroshenkoam.allure.client.dto.Page;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface LaunchService {

    /**
     * Create a new launch. Created launch will linked with test pack
     * found by testPackId or testPackExternalId provided in event body. If there is no
     * such test pack, a new one will be created.
     */
    @POST("api/rs/launch/new")
    Call<Launch> create(
            @Body CreateLaunchEvent event
    );

    /**
     * Finds launch by id.
     */
    @GET("api/rs/launch/{id}")
    Call<Launch> findById(
            @Path("id") Long id
    );

    /**
     * Finds launches by rql.
     */
    @GET("api/rs/launch/__search")
    Call<Page<Launch>> findAll(@Query("projectId") Long projectId,
                               @Query("rql") String rql,
                               @Query("page") int page,
                               @Query("size") int size);

    /**
     * Delete by launch id.
     */
    @DELETE("api/rs/launch/{id}")
    Call<Launch> delete(
            @Path("id") Long id
    );

}
