package transformers;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link AsyncResponseTransformer} that creates an Akka Source from the content
 *
 * @param <ResponseT> Pojo response type.
 */
public class SourceAsyncResponseTransformer<ResponseT> implements AsyncResponseTransformer<ResponseT, ResponseSource<ResponseT>> {
    private volatile CompletableFuture<Source<ByteString, ?>> cf;
    private volatile ResponseT response;

    @Override
    public CompletableFuture<ResponseSource<ResponseT>> prepare() {
        cf = new CompletableFuture<>();
        return cf.thenApply(source -> new ResponseSource<>(response, source));
    }

    @Override
    public void onResponse(ResponseT response) {
        this.response = response;
    }

    @Override
    public void onStream(SdkPublisher<ByteBuffer> publisher) {
        cf.complete(Source.fromPublisher(publisher).map(ByteString::fromByteBuffer).filter(ByteString::nonEmpty));
    }

    @Override
    public void exceptionOccurred(Throwable throwable) {
        cf.completeExceptionally(throwable);
    }
}
