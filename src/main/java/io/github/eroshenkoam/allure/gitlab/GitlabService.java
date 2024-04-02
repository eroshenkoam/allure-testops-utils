package io.github.eroshenkoam.allure.gitlab;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface GitlabService {

    @GET("api/v4/users")
    Call<List<GitlabMember>> getUser(@Query("username") String username);

    @GET("api/v4/users/{id}/memberships")
    Call<List<GitlabMembership>> getUserMembers(@Path("id") final Long id);

    @GET("api/v4/groups/{id}")
    Call<GitlabGroup> getGroup(@Path("id") final Long id);

    @GET("api/v4/groups/{id}/subgroups")
    Call<List<GitlabGroup>> getSubgroups(@Path("id") final Long id);

    @GET("api/v4/groups/{id}/projects")
    Call<List<Void>> getProjects(@Path("id") final Long id);

}
