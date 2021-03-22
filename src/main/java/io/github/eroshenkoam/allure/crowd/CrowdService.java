package io.github.eroshenkoam.allure.crowd;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface CrowdService {

    @GET("rest/usermanagement/1/user")
    Call<CrowdUser> getUser(@Query("username") final String username);

    @GET("rest/usermanagement/1/user/group/direct")
    Call<CrowdGroups> getUserDirectGroups(@Query("username") final String username);

    @GET("rest/usermanagement/1/user/group/nested")
    Call<CrowdGroups> getUserNestedGroups(@Query("username") final String username);

    @GET("rest/usermanagement/1/user/group/nested")
    Call<ResponseBody> getUserNestedGroupsBody(@Query("username") final String username);

}
