package transformers;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.util.Objects;

/**
 * A representation of the service's response from a streaming operation that provides the content as an akka source.
 *
 * @param <ResponseT> Pojo response type.
 */
public class ResponseSource<ResponseT> {
    private final ResponseT response;
    private final Source<ByteString, ?> source;

    public ResponseSource(ResponseT response, Source<ByteString, ?> source) {
        this.source = Objects.requireNonNull(source);
        this.response = Objects.requireNonNull(response);
    }

    /**
     * @return the unmarshalled response object from the service.
     */
    public ResponseT response() {
        return response;
    }

    /**
     * @return The output as an akka source.
     */
    public Source<ByteString, ?> asSource() {
        return source;
    }
}
