package filters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;
import play.libs.ws.BodyWritable;
import play.libs.ws.InMemoryBodyWritable;
import play.libs.ws.SourceBodyWritable;
import play.libs.ws.WSRequestExecutor;
import play.libs.ws.WSRequestFilter;
import play.libs.ws.WSSignatureCalculator;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase;
import play.shaded.ahc.org.asynchttpclient.SignatureCalculator;
import play.shaded.ahc.org.asynchttpclient.uri.Uri;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.LazyAwsRegionProvider;

/**
 * Base class for signing AWS requests
 * 
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html">sigv4_signing</a>
 * @see <a href="https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-call-api.html">how-to-call-api</a>
 */
public class AwsSignatureFilter implements WSRequestFilter {
	private static final String API_GATEWAY_SERVICE_NAME = "execute-api";
	private static final AwsRegionProvider DEFAULT_REGION_PROVIDER = new LazyAwsRegionProvider(DefaultAwsRegionProviderChain::new);
	private final Materializer materializer;
	private final Region region;

	public AwsSignatureFilter(Materializer materializer) {
		this.materializer = Objects.requireNonNull(materializer);
		this.region = DEFAULT_REGION_PROVIDER.getRegion();
	}

	@Override
	public WSRequestExecutor apply(WSRequestExecutor requestExecutor) {
		return request -> {
			BodyWritable<?> bodyWritable = request.getBody().orElse(null);
			request.sign(new AwsSignatureCalculator(bodyWritable));
			return requestExecutor.apply(request);
		};
	}

	private class AwsSignatureCalculator implements WSSignatureCalculator, SignatureCalculator {
		private final BodyWritable<?> bodyWritable;

		private AwsSignatureCalculator(BodyWritable<?> bodyWritable) {
			this.bodyWritable = bodyWritable;
		}

		@Override
		public void calculateAndAddSignature(Request unsignedRequest, RequestBuilderBase<?> requestBuilder) {
			SdkHttpFullRequest signedRequest = this.getSignedRequest(unsignedRequest);
			requestBuilder.setHeaders(signedRequest.headers());
		}

		private SdkHttpFullRequest getSignedRequest(Request unsignedRequest) {
			Uri uri = unsignedRequest.getUri();
			try(InputStream contentStream = this.getContentStream()) {
				SdkHttpFullRequest fullRequest = SdkHttpFullRequest.builder()
						.contentStreamProvider(() -> contentStream)
						.protocol(uri.getScheme())
						.host(uri.getHost())
						.encodedPath(uri.getPath())
						.method(SdkHttpMethod.fromValue(unsignedRequest.getMethod()))
						.applyMutation(mutator -> {
							unsignedRequest.getHeaders().forEach(h -> mutator.appendHeader(h.getKey(), h.getValue()));
							unsignedRequest.getQueryParams().forEach(p -> mutator.appendRawQueryParameter(decode(p.getName()), decode(p.getValue())));
						})
						.build();

				Aws4SignerParams signingParams = Aws4SignerParams.builder()
						.awsCredentials(DefaultCredentialsProvider.create().resolveCredentials())
						.signingName(API_GATEWAY_SERVICE_NAME)
						.signingRegion(region)
						.build();

				return Aws4Signer.create().sign(fullRequest, signingParams);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private InputStream getContentStream() {
			if (bodyWritable instanceof InMemoryBodyWritable) {
				ByteString byteString = ((InMemoryBodyWritable) bodyWritable).body().get();
				return new ByteArrayInputStream(byteString.toArray());
			} else if (bodyWritable instanceof SourceBodyWritable) {
				Source<ByteString, ?> source = ((SourceBodyWritable) bodyWritable).body().get();
				return source.runWith(StreamConverters.asInputStream(Duration.ofSeconds(30)), materializer);
			} else {
				return new ByteArrayInputStream(new byte[0]);
			}
		}

		private String decode(String encoded) {
			try {
				return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
