package io.github.eroshenkoam.allure.client;

import io.github.eroshenkoam.allure.client.dto.TestCaseAttachment;
import io.github.eroshenkoam.allure.client.dto.CustomFieldValue;
import io.github.eroshenkoam.allure.client.dto.Issue;
import io.github.eroshenkoam.allure.client.dto.Member;
import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.TestCasePatch;
import io.github.eroshenkoam.allure.client.dto.TestCaseScenario;
import io.github.eroshenkoam.allure.client.dto.TestCase;
import io.github.eroshenkoam.allure.client.dto.TestCaseRelation;
import io.github.eroshenkoam.allure.client.dto.TestTag;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@SuppressWarnings("PMD.LinguisticNaming")
public interface TestCaseService {

    /**
     * Find test case by id.
     */
    @GET("api/rs/testcase/{id}")
    Call<TestCase> findById(@Path("id") Long id);

    default Call<Page<TestCase>> findByRql(
            @Query("projectId") Long projectId,
            @Query("rql") String rql,
            @Query("page") int page,
            @Query("size") int size) {
        return findByRql(projectId, rql, false, page, size);
    }

    /**
     * Find test cases in project by rql.
     */
    @GET("api/rs/testcase/__search")
    Call<Page<TestCase>> findByRql(
            @Query("projectId") Long projectId,
            @Query("rql") String rql,
            @Query("deleted") boolean deleted,
            @Query("page") int page,
            @Query("size") int size
    );

    /**
     * Create test case in project.
     */
    @POST("api/rs/testcase")
    Call<TestCase> create(@Body TestCase testCase);

    /**
     * Update test case by id.
     */
    @Headers(
            "Content-Type: application/json"
    )
    @PATCH("api/rs/testcase/{id}")
    Call<TestCase> update(@Path("id") Long testCaseId, @Body TestCasePatch patch);

    /**
     * Delete test case by id.
     */
    @DELETE("api/rs/testcase/{id}")
    Call<Void> delete(@Path("id") Long testCaseId);

    /**
     * Fetch test case tags.
     */
    @GET("api/rs/testcase/{id}/link")
    Call<List<TestTag>> getLinks(@Path("id") Long id);

    /**
     * Update test case tags.
     */
    @POST("api/rs/testcase/{id}/link")
    Call<List<TestTag>> setLinks(@Path("id") Long id, @Body List<TestTag> values);

    /**
     * Fetch test case tags.
     */
    @GET("api/rs/testcase/{id}/tag")
    Call<List<TestTag>> getTags(@Path("id") Long id);

    /**
     * Update test case tags.
     */
    @POST("api/rs/testcase/{id}/tag")
    Call<List<TestTag>> setTags(@Path("id") Long id, @Body List<TestTag> values);

    /**
     * Fetch test case issues.
     */
    @GET("api/rs/testcase/{id}/issue")
    Call<List<Issue>> getIssues(@Path("id") Long id);

    /**
     * Update test case issues.
     */
    @POST("api/rs/testcase/{id}/issue")
    Call<List<Issue>> setIssues(@Path("id") Long id, @Body List<Issue> values);

    /**
     * Fetch test case issues.
     */
    @GET("api/rs/testcase/{id}/members")
    Call<List<Member>> getMembers(@Path("id") Long id);

    /**
     * Update test case issues.
     */
    @POST("api/rs/testcase/{id}/members")
    Call<List<Member>> setMembers(@Path("id") Long id, @Body List<Member> values);

    /**
     * Fetch test case custom fields.
     */
    @GET("api/rs/testcase/{id}/cfv")
    Call<List<CustomFieldValue>> getCustomFields(@Path("id") Long id);

    /**
     * Update test case custom fields.
     */
    @POST("api/rs/testcase/{id}/cfv")
    Call<List<CustomFieldValue>> setCustomFields(@Path("id") Long id, @Body List<CustomFieldValue> values);

    /**
     * Get test case relations.
     */
    @GET("api/rs/testcase/{id}/relation")
    Call<List<TestCaseRelation>> getRelations(@Path("id") Long testCaseId);

    /**
     * Set test case relations.
     */
    @POST("api/rs/testcase/{id}/relation")
    Call<List<TestCaseRelation>> setRelations(@Path("id") Long testCaseId, @Body List<TestCaseRelation> relations);

    /**
     * Get test cases scenario.
     */
    @GET("api/rs/testcase/{id}/scenario")
    Call<TestCaseScenario> getScenario(@Path("id") Long id);

    /**
     * Get test cases scenario.
     */
    @GET("api/rs/testcase/{id}/scenariofromrun")
    Call<TestCaseScenario> getScenarioFromRun(@Path("id") Long id);

    /**
     * Set test cases scenario.
     */
    @POST("api/rs/testcase/{id}/scenario")
    Call<Void> setScenario(@Path("id") Long id, @Body TestCaseScenario scenario);

    /**
     * Set test cases scenario.
     */
    @DELETE("api/rs/testcase/{id}/scenario")
    Call<Void> deleteScenario(@Path("id") Long id);

    /**
     * Get test case attachments.
     */
    @GET("api/rs/testcase/attachment")
    Call<Page<TestCaseAttachment>> getAttachments(@Query("testCaseId") Long testCaseId,
                                                  @Query("page") int page,
                                                  @Query("size") int size);

    @GET("/api/rs/testcase/attachment/{id}/content")
    Call<ResponseBody> getAttachmentContent(@Path("id") Long id);

    /**
     * Upload attachment for specific test case.
     */
    @Multipart
    @POST("api/rs/testcase/attachment")
    Call<List<TestCaseAttachment>> addAttachment(@Query("testCaseId") Long testCaseId,
                                                 @Part List<MultipartBody.Part> files);

    @DELETE("api/rs/testcase/attachment/{attachmentId}")
    Call<Void> deleteAttachment(@Path("attachmentId") Long attachmentId);


}
