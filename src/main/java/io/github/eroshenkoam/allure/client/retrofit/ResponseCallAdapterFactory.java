package io.github.eroshenkoam.allure.client.retrofit;

import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ResponseCallAdapterFactory<T> extends CallAdapter.Factory {

    @Override
    public CallAdapter<T, ?> get(final Type returnType,
                                 final Annotation[] annotations,
                                 final Retrofit retrofit) {
        if (returnType.getTypeName().startsWith(Response.class.getName())) {
            return new ResponseCallAdapter((ParameterizedType) returnType);
        }
        return null;
    }

    private class ResponseCallAdapter implements CallAdapter<T, Response<T>> {

        private final ParameterizedType returnType;

        ResponseCallAdapter(final ParameterizedType returnType) {
            this.returnType = returnType;
        }

        @Override
        public Type responseType() {
            return returnType.getActualTypeArguments()[0];
        }

        @Override
        public Response<T> adapt(final Call<T> call) {
            try {
                return call.execute();
            } catch (IOException e) {
                throw new ResponseException(e);
            }
        }
    }

}
