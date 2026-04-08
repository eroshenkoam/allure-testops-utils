package io.github.eroshenkoam.allure.client;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.Project;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.List;

public interface ProjectService {

    @GET("api/rs/project")
    Call<Page<Project>> getProjects(@Query("query") String query,
                                    @Query("page") int page,
                                    @Query("size") int size);

    @GET("api/rs/project/{id}")
    Call<Project> getProject(@Path("id") Long projectId);

    @GET("api/rs/project")
    Call<List<Project>> getProjectList();


    default List<Project> getProjectsWrapper() throws IOException {
        try {
            return getProjectList().execute().body();
        } catch (MismatchedInputException e) {
            return getProjects("", 0, 1000).execute().body().getContent();
        }
    }

}
