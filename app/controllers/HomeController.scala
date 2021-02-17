package controllers

import java.time.{Duration, Instant}

import akka.stream.scaladsl.StreamConverters
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import controllers.HomeController.PresignedUrls
import javax.inject._
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{SecureAction, Security, SecurityComponents}
import play.api.mvc._
import play.api.{Configuration, Environment}
import software.amazon.awssdk.auth.credentials.{AwsSessionCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.cognitoidentity.model.{GetCredentialsForIdentityRequest, GetIdRequest}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, ListObjectsRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PresignedGetObjectRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: SecurityComponents)(implicit val ec: ExecutionContext) extends Security[CommonProfile] {

  val secureAction: SecureAction[CommonProfile, AnyContent, AuthenticatedRequest] = Secure("OidcClient")
  private val bucket = "tdr-consignment-export-intg"

  implicit class RequestToRequestWithToken(request: Request[AnyContent]) {
    def token = {
      val webContext = new PlayWebContext(request, playSessionStore)
      val profileManager = new ProfileManager[CommonProfile](webContext)
      val profile = profileManager.get(true)
      profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    }
  }

  private def s3Client(token: String) = {
    val provider: StaticCredentialsProvider = credentialsProvider(token)
    S3Client.builder().region(Region.EU_WEST_2)
      .credentialsProvider(provider).build()
  }

  private def credentialsProvider(token: String) = {
    val client = CognitoIdentityClient.builder().region(Region.EU_WEST_2).build()
    val configuration = Configuration.load(Environment.simple())
    val identityPool = configuration.get[String]("cognito.identity-pool")
    val accountId = configuration.get[String]("account-id")
    val authUrl = configuration.get[String]("auth.url")
    val login = s"$authUrl/auth/realms/downloadspike"
    val getIdRequest = GetIdRequest.builder().accountId(accountId).identityPoolId(identityPool).logins(Map(login -> token).asJava).build()
    val idRequest = client.getId(getIdRequest)
    val identityResponse = GetCredentialsForIdentityRequest.builder().identityId(idRequest.identityId()).logins(Map(login -> token).asJava).build()
    val credentials = client.getCredentialsForIdentity(identityResponse)
    val accessKey = credentials.credentials().accessKeyId()
    val secretKey = credentials.credentials().secretKey()
    val sessionToken = credentials.credentials().sessionToken()
    val credentialsProvider = StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
    credentialsProvider
  }

  def index(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val objs = s3Client(request.token.toString).listObjects(ListObjectsRequest.builder().bucket(bucket).build())
    val keys: List[String] = objs.contents().asScala.map(_.key()).toList
    Future(Ok(views.html.index(keys)))
  }

  def presigned(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    val objs = s3Client(request.token.toString).listObjects(ListObjectsRequest.builder().bucket(bucket).build())
    val presigner = S3Presigner.builder().credentialsProvider(credentialsProvider(request.token.toString)).build()
    val keys: List[PresignedUrls] = objs.contents().asScala.map(s3 => {
      val key = s3.key()
      val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
      val presignReq = GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10)).getObjectRequest(req).build()
      PresignedUrls(key,presigner.presignGetObject(presignReq).url().toString)
    }).toList

    Ok(views.html.presigned(keys))
  }

  def download(key: String): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] => {
    val response = s3Client(request.token.toString).getObject(
      GetObjectRequest.builder().bucket(bucket).key(key).build(),
    )
    val s = StreamConverters.fromInputStream(() => response, 100)
    Ok.streamed(s, None)
  }
  }
}

object HomeController {
  case class PresignedUrls(key: String, url: String)
}
