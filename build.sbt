name := "play-aws"

scalaVersion := "2.12.10"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18)
EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.withSource := true
EclipseKeys.withJavadoc := true

retrieveManaged := true

libraryDependencies ++= Seq(
	guice,
	javaWs,
	"software.amazon.awssdk" % "aws-core" % "2.9.26"
)
