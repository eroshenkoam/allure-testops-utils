package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.Group;
import io.github.eroshenkoam.allure.client.dto.GroupUser;
import io.github.eroshenkoam.allure.client.dto.GroupUserAdd;
import io.github.eroshenkoam.allure.client.dto.Page;
import retrofit2.Call;
import retrofit2.http.*;

public interface GroupService {

    @GET("/api/rs/accessgroup")
    Call<Page<Group>> find(
            @Query("query") String query,
            @Query("page") int page,
            @Query("size") int size
    );

    @POST("/api/rs/accessgroup")
    Call<Group> create(@Body Group dto);

    @GET("/api/rs/accessgroup/{id}/user")
    Call<Page<GroupUser>> getUsers(@Path("id") Long groupId);

    @POST("/api/rs/accessgroup/{id}/user")
    Call<Void> addUsers(@Path("id") Long groupId, @Body GroupUserAdd dto);

    @DELETE("/api/rs/accessgroup/{id}/user")
    Call<Void> removeUser(@Path("id") Long groupId, @Query("username") String username);

}
