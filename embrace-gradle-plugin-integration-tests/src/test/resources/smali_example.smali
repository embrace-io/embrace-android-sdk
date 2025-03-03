.class public final Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;


# annotations
.annotation runtime Lio/embrace/android/embracesdk/internal/config/instrumented/EmbraceInstrumented;
.end annotation


# static fields
.field public static final INSTANCE:Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;

    invoke-direct {v0}, Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;-><init>()V

    sput-object v0, Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;->INSTANCE:Lio/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl;

    return-void
.end method

.method private constructor <init>()V
    .locals 0

    .line 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 2
    .line 3
    .line 4
    return-void
.end method


# virtual methods
.method public getAppFramework()Ljava/lang/String;
    .locals 1

    .line 1
    invoke-static {p0}, Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig$DefaultImpls;->getAppFramework(Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;)Ljava/lang/String;

    .line 2
    .line 3
    .line 4
    move-result-object v0

    .line 5
    return-object v0
.end method

.method public getAppId()Ljava/lang/String;
    .locals 1

    .line 1
    invoke-static {p0}, Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig$DefaultImpls;->getAppId(Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;)Ljava/lang/String;

    .line 2
    .line 3
    .line 4
    const-string v0, "abcde"

    .line 5
    .line 6
    return-object v0
.end method

.method public getBuildFlavor()Ljava/lang/String;
    .locals 1

    .line 1
    invoke-static {p0}, Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig$DefaultImpls;->getBuildFlavor(Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;)Ljava/lang/String;

    .line 2
    .line 3
    .line 4
    const-string v0, ""

    .line 5
    .line 6
    return-object v0
.end method

.method public getBuildId()Ljava/lang/String;
    .locals 1

    .line 1
    invoke-static {p0}, Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig$DefaultImpls;->getBuildId(Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;)Ljava/lang/String;

    .line 2
    .line 3
    .line 4
    const-string v0, "68B76DEAB6B9476A9B76092EB9A3E884"

    .line 5
    .line 6
    return-object v0
.end method

.method public getBuildType()Ljava/lang/String;
    .locals 1

    .line 1
    invoke-static {p0}, Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig$DefaultImpls;->getBuildType(Lio/embrace/android/embracesdk/internal/config/instrumented/schema/ProjectConfig;)Ljava/lang/String;

    .line 2
    .line 3
    .line 4
    const-string v0, "release"

    .line 5
    .line 6
    return-object v0
.end method
