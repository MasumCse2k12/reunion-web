// =============================================================================
//  Sammalani Alumni — build & deploy pipeline
//  Source: https://github.com/MasumCse2k12/reunion-web
// =============================================================================
//
//  Builds web/ and deploys it to Vercel. `main` goes to production; every other
//  branch gets a throwaway preview URL, which is what you send the committee
//  before you promote anything.
//
//  It also builds the two container images — web/Dockerfile and server/Dockerfile
//  — and proves the web one serves. Vercel does not use those images; the stage
//  exists so the Dockerfiles cannot rot between the days you actually deploy
//  them, and so there is something to push the day you move off Vercel.
//
// -----------------------------------------------------------------------------
//  A. What the Jenkins machine needs
// -----------------------------------------------------------------------------
//   - Plugins: Pipeline, Git, Docker Pipeline. (Optional: GitHub, for webhooks.)
//   - Docker available to the Jenkins user:  sudo usermod -aG docker jenkins
//     Then restart Jenkins. Verify with:     sudo -u jenkins docker run --rm hello-world
//     No Docker? See section E.
//   - Outbound HTTPS to github.com, registry.npmjs.org and vercel.com.
//   - Disk. The image stage keeps one tag per branch plus Docker's build cache;
//     budget a few GB and run `docker system prune -f` from cron monthly.
//
// -----------------------------------------------------------------------------
//  B. The one secret: a Vercel token
// -----------------------------------------------------------------------------
//   1. Create a token at https://vercel.com/account/tokens (scope: Full Account).
//   2. Jenkins → Manage Jenkins → Credentials → System → Global credentials → Add:
//          Kind:  Secret text
//          Secret: <paste the Vercel token>
//          ID:     vercel-token          <-- the ID must match exactly
//   VERCEL_ORG_ID and VERCEL_PROJECT_ID are hardcoded below on purpose. They are
//   public identifiers — they say *which* project to deploy, not who may deploy
//   it. The token is the only thing that grants access, so it is the only thing
//   that lives in Jenkins credentials.
//
//   A registry credential is optional, and only needed once you want the images
//   kept somewhere rather than just built:
//          Kind:     Username with password
//          Username: <registry username>
//          Password: <registry token — not your account password>
//          ID:       docker-registry
//   ...then set DOCKER_REGISTRY below to e.g. 'ghcr.io/masumcse2k12' or
//   'docker.io/masumcse2k12'. While it is empty the images are built, verified
//   and left on the agent, and nothing is pushed anywhere.
//
//   GitHub credentials are NOT needed while reunion-web is a public repository.
//   If you ever make it private, add a second credential:
//          Kind: Username with password
//          Username: MasumCse2k12
//          Password: <a GitHub personal access token with `repo` scope>
//          ID:       github-reunion-web
//   ...and set GIT_CREDENTIALS_ID below to 'github-reunion-web'.
//
// -----------------------------------------------------------------------------
//  C. Create the job — Multibranch (recommended)
// -----------------------------------------------------------------------------
//   Gives you a production deploy on `main` and an automatic preview URL for
//   every other branch and pull request.
//
//   Jenkins → New Item → name: reunion-web → **Multibranch Pipeline** → OK
//     Branch Sources → Add source → Git
//         Project Repository: https://github.com/MasumCse2k12/reunion-web.git
//         Credentials:        - none -            (public repo)
//     Build Configuration
//         Mode:        by Jenkinsfile
//         Script Path: Jenkinsfile
//     Scan Repository Triggers
//         [x] Periodically if not otherwise run — Interval: 5 minutes
//     Save. Jenkins scans the repo and builds `main` immediately.
//
//   For instant builds instead of 5-minute polling, add a webhook on GitHub:
//     Repo → Settings → Webhooks → Add webhook
//         Payload URL:  https://<your-jenkins-host>/github-webhook/
//         Content type: application/json
//         Events:       Just the push event
//   Your Jenkins must be reachable from the internet for this. If it is not,
//   leave the 5-minute scan on — for a project with one committer that is fine.
//
// -----------------------------------------------------------------------------
//  D. Create the job — single Pipeline (simpler, one branch)
// -----------------------------------------------------------------------------
//   Jenkins → New Item → name: reunion-web-deploy → **Pipeline** → OK
//     [x] This project is parameterised   (the parameters block below fills in
//                                          DEPLOY_BRANCH and SKIP_DEPLOY on the
//                                          first run — run it once, then the
//                                          "Build with Parameters" button appears)
//     Pipeline
//         Definition:  Pipeline script from SCM
//         SCM:         Git
//         Repository URL: https://github.com/MasumCse2k12/reunion-web.git
//         Credentials:    - none -
//         Branch:      */main
//         Script Path: Jenkinsfile
//     Save → Build Now.
//
//   The Checkout stage below also works if you paste this file straight into the
//   inline "Pipeline script" box: with no SCM bound to the job it clones
//   GIT_REPO at DEPLOY_BRANCH itself.
//
// -----------------------------------------------------------------------------
//  E. No Docker on the agent?
// -----------------------------------------------------------------------------
//   Replace the whole `agent { docker { … } }` block with `agent any`, and make
//   sure the agent has Node >= 22.12 on its PATH — Vite 8 refuses to start on
//   anything older, and Debian/Ubuntu ship Node 18. Either:
//       curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs
//   or install the NodeJS plugin, configure an installation named `node-22`,
//   and add this just under `agent`:
//       tools { nodejs 'node-22' }
//
//   Then also tick SKIP_IMAGES, or set its default to true below. An agent with
//   no Docker cannot build images, and that stage will fail rather than skip.
//
// -----------------------------------------------------------------------------
//  F. First run — what you should see
// -----------------------------------------------------------------------------
//   Checkout → Install → Typecheck → Build → Docker images →
//   Deploy — production → Smoke test
//   and a build description of "production → https://…vercel.app".
//   If it dies in Install with EACCES, Docker is running the container as root
//   despite the args below — check that no global Jenkins config forces `-u 0`.
//   If it dies in Deploy with "Not authorized", the vercel-token credential ID
//   is wrong or the token was revoked.
//   If it dies in Docker images with "permission denied … /var/run/docker.sock",
//   the jenkins user is not in the docker group — section A.
// =============================================================================

// Checks the source out into whatever workspace is current. The image stage runs
// on its own agent and so gets its own empty workspace, which is why this is a
// function and not just the body of the Checkout stage.
def checkoutSource() {
  // A Multibranch job — or "Pipeline script from SCM" — already has `scm` bound.
  // A job with this script pasted inline does not, so fall back to cloning
  // GIT_REPO directly rather than failing.
  try {
    checkout scm
  } catch (Exception ignored) {
    // Reflection on the exception would trip the Groovy sandbox; the only thing
    // that matters here is that `scm` was not available.
    echo "No SCM bound to this job — cloning ${env.GIT_REPO} directly."
    if (env.GIT_CREDENTIALS_ID?.trim()) {
      git url: env.GIT_REPO, branch: params.DEPLOY_BRANCH, credentialsId: env.GIT_CREDENTIALS_ID
    } else {
      git url: env.GIT_REPO, branch: params.DEPLOY_BRANCH
    }
  }
}

pipeline {
  agent {
    docker {
      // The full image, not -slim / -alpine: those omit git, which `checkout scm` needs.
      image 'node:22'
      // Jenkins runs the container as its own uid, which owns no home directory
      // inside the image. Without these, npm and npx die with EACCES on /root
      // during the very first build. Writing caches to /tmp keeps the container
      // non-root, so nothing root-owned is left behind in a shared workspace.
      args '-e HOME=/tmp -e NPM_CONFIG_CACHE=/tmp/.npm -e XDG_CACHE_HOME=/tmp/.cache'
    }
  }

  parameters {
    string(
      name: 'DEPLOY_BRANCH',
      defaultValue: 'main',
      description: 'Branch to build. Ignored by Multibranch jobs, which already know their branch.'
    )
    booleanParam(
      name: 'SKIP_DEPLOY',
      defaultValue: false,
      description: 'Build and typecheck only — do not deploy to Vercel.'
    )
    booleanParam(
      name: 'SKIP_IMAGES',
      defaultValue: false,
      description: 'Do not build the container images. Required on an agent without Docker — see section E.'
    )
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '30'))
    // 20 was enough when this only built the web app. A cold image build pulls
    // base images and resolves the whole Gradle dependency graph before it
    // compiles a line; that alone can run past 20 on a slow link.
    timeout(time: 45, unit: 'MINUTES')
  }

  environment {
    APP_DIR            = 'web'
    GIT_REPO           = 'https://github.com/MasumCse2k12/reunion-web.git'
    // Empty while the repository is public — see section B.
    GIT_CREDENTIALS_ID = ''
    VERCEL_ORG_ID      = 'team_K0rTR9Nb455AyzPAXgNebJxR'
    VERCEL_PROJECT_ID  = 'prj_rDfnxa5e6gAcYwC9pceiZj8B2Qr7'
    // Empty means build the images and keep them on the agent, push nothing.
    // Set it to a registry namespace — 'ghcr.io/masumcse2k12' — and add the
    // docker-registry credential to start pushing. See section B.
    DOCKER_REGISTRY    = ''
    IMAGE_WEB          = 'sammalani/alumni-web'
    IMAGE_API          = 'sammalani/alumni-api'
    CI                 = 'true'
    NPM_CONFIG_FUND    = 'false'
    NPM_CONFIG_AUDIT   = 'false'
  }

  stages {

    stage('Checkout') {
      steps {
        script {
          checkoutSource()

          // Multibranch sets BRANCH_NAME; the other two job types do not. Resolve
          // it once here so the deploy stages below have one thing to test.
          env.TARGET_BRANCH = env.BRANCH_NAME?.trim() ?: params.DEPLOY_BRANCH
          echo "Building branch: ${env.TARGET_BRANCH}"

          // Two tags per image: the commit, which is the one you can roll back
          // to, and the branch, which is the one you type. A branch name may
          // contain '/' and a Docker tag may not.
          env.IMAGE_TAG        = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
          env.IMAGE_BRANCH_TAG = env.TARGET_BRANCH.replaceAll('[^A-Za-z0-9_.-]', '-')
        }
        sh 'git --no-pager log -1 --pretty="%h %an %s"'
      }
    }

    stage('Install') {
      steps {
        dir(env.APP_DIR) {
          sh 'node -v && npm -v'
          // `npm ci` not `npm install` — the lockfile is the contract.
          sh 'npm ci'
        }
      }
    }

    // Typecheck and build are separate on purpose: when this pipeline goes red
    // you want to know at a glance whether it was the types or the bundler.
    stage('Typecheck') {
      steps {
        dir(env.APP_DIR) {
          sh 'npm run typecheck'
        }
      }
    }

    stage('Build') {
      steps {
        dir(env.APP_DIR) {
          sh 'npm run build'
          sh 'ls -lh dist dist/assets'
        }
      }
      post {
        success {
          archiveArtifacts artifacts: "${env.APP_DIR}/dist/**", fingerprint: true
        }
      }
    }

    // Runs on the host agent, not in the node:22 container the stages above use:
    // a container has no Docker daemon to build with. That means its own empty
    // workspace, hence the second checkout.
    stage('Docker images') {
      agent any
      when {
        // Decide before allocating the agent — skipping should cost nothing.
        beforeAgent true
        expression { return !params.SKIP_IMAGES }
      }
      steps {
        script {
          checkoutSource()

          // BuildKit gives us the cache mounts both Dockerfiles rely on; without
          // it every build re-downloads every npm and every Gradle dependency.
          withEnv(['DOCKER_BUILDKIT=1']) {
            sh 'docker build -t "$IMAGE_WEB:$IMAGE_TAG" -t "$IMAGE_WEB:$IMAGE_BRANCH_TAG" web'

            // The backend is younger than this pipeline. Build it when it is
            // there and say so when it is not, rather than failing a web-only
            // commit on a directory that does not exist yet.
            if (fileExists('server/Dockerfile')) {
              sh 'docker build -t "$IMAGE_API:$IMAGE_TAG" -t "$IMAGE_API:$IMAGE_BRANCH_TAG" server'
            } else {
              echo 'No server/Dockerfile in this commit — skipping the API image.'
            }
          }

          // An image that builds is not an image that serves. No published port:
          // two concurrent branch builds would fight over it, and a host port is
          // not needed to ask nginx a question.
          sh '''
            set -e
            # Named by container id, not by BUILD_NUMBER: two branches of a
            # Multibranch job can be on the same build number at the same time,
            # on this same agent.
            check=$(docker run -d "$IMAGE_WEB:$IMAGE_TAG")
            echo "$check" > .web-check-cid

            for i in $(seq 1 30); do
              docker exec "$check" wget -qO /dev/null http://127.0.0.1/healthz && break
              sleep 1
            done

            # busybox wget exits non-zero on anything but a 2xx, so this is the
            # assertion. /admin/login has no file behind it — it is the SPA
            # fallback, the thing that breaks first and most embarrassingly.
            for path in / /admin/login /app/profile; do
              docker exec "$check" wget -qO /dev/null "http://127.0.0.1$path"
              echo "$path -> ok"
            done
          '''

          // Nothing to push to until DOCKER_REGISTRY is set, and no reason to
          // push a branch: it is `main` that anyone would deploy.
          if (env.DOCKER_REGISTRY?.trim() && env.TARGET_BRANCH == 'main') {
            withCredentials([usernamePassword(
              credentialsId: 'docker-registry',
              usernameVariable: 'REGISTRY_USER',
              passwordVariable: 'REGISTRY_TOKEN'
            )]) {
              sh '''
                set -e
                # DOCKER_REGISTRY is host plus namespace; docker login wants the host.
                echo "$REGISTRY_TOKEN" | docker login "${DOCKER_REGISTRY%%/*}" -u "$REGISTRY_USER" --password-stdin

                for image in "$IMAGE_WEB" "$IMAGE_API"; do
                  # Skipped rather than failed: the API image does not exist on a
                  # commit that has no server/Dockerfile.
                  docker image inspect "$image:$IMAGE_TAG" >/dev/null 2>&1 || continue
                  for tag in "$IMAGE_TAG" "$IMAGE_BRANCH_TAG"; do
                    docker tag  "$image:$tag" "$DOCKER_REGISTRY/$image:$tag"
                    docker push "$DOCKER_REGISTRY/$image:$tag"
                  done
                done
              '''
            }
            echo "Pushed ${env.IMAGE_TAG} to ${env.DOCKER_REGISTRY}."
          } else {
            echo 'DOCKER_REGISTRY is unset or this is not main — images built and left on the agent.'
          }
        }
      }
      post {
        // `failure` runs before `cleanup`, which is the whole reason the removal
        // below is not in an `always` block: that one runs first and would bin
        // the container before anyone could read why it failed.
        failure {
          sh '''
            [ -f .web-check-cid ] || exit 0
            echo '--- nginx log from the failed check container ---'
            docker logs "$(cat .web-check-cid)" 2>&1 | tail -40 || true
          '''
        }
        cleanup {
          // Jenkins runs sh with -xe, and `docker image rm` on an image that was
          // never built returns non-zero. Every command here therefore ends in
          // `|| true`: cleanup must never be the thing that turns a build red.
          sh '''
            [ -f .web-check-cid ] && docker rm -f "$(cat .web-check-cid)" >/dev/null 2>&1
            rm -f .web-check-cid || true
            [ -n "$DOCKER_REGISTRY" ] && docker logout "${DOCKER_REGISTRY%%/*}" >/dev/null 2>&1

            # The per-commit tags would otherwise accumulate one image per build
            # forever. The branch tags stay, so `docker run sammalani/alumni-web:main`
            # works on the agent, and the layers stay in the build cache either way.
            docker image rm -f "$IMAGE_WEB:$IMAGE_TAG" >/dev/null 2>&1 || true
            docker image rm -f "$IMAGE_API:$IMAGE_TAG" >/dev/null 2>&1 || true
            if [ -n "$DOCKER_REGISTRY" ]; then
              docker image rm -f "$DOCKER_REGISTRY/$IMAGE_WEB:$IMAGE_TAG" >/dev/null 2>&1 || true
              docker image rm -f "$DOCKER_REGISTRY/$IMAGE_API:$IMAGE_TAG" >/dev/null 2>&1 || true
            fi
            exit 0
          '''
        }
      }
    }

    stage('Deploy — preview') {
      when {
        allOf {
          expression { return !params.SKIP_DEPLOY }
          expression { return env.TARGET_BRANCH != 'main' }
        }
      }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir(env.APP_DIR) {
            sh '''
              set -e
              npx --yes vercel@latest pull  --yes --environment=preview --token="$VERCEL_TOKEN"
              npx --yes vercel@latest build --token="$VERCEL_TOKEN"
              npx --yes vercel@latest deploy --prebuilt --token="$VERCEL_TOKEN" > .deploy-url
            '''
            script {
              env.DEPLOY_URL = readFile('.deploy-url').trim()
              currentBuild.description = "preview → ${env.DEPLOY_URL}"
            }
          }
        }
      }
    }

    stage('Deploy — production') {
      when {
        allOf {
          expression { return !params.SKIP_DEPLOY }
          expression { return env.TARGET_BRANCH == 'main' }
        }
      }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir(env.APP_DIR) {
            sh '''
              set -e
              npx --yes vercel@latest pull  --yes --environment=production --token="$VERCEL_TOKEN"
              npx --yes vercel@latest build --prod --token="$VERCEL_TOKEN"
              npx --yes vercel@latest deploy --prebuilt --prod --token="$VERCEL_TOKEN" > .deploy-url
            '''
            script {
              env.DEPLOY_URL = readFile('.deploy-url').trim()
              currentBuild.description = "production → ${env.DEPLOY_URL}"
            }
          }
        }
      }
    }

    stage('Smoke test') {
      when {
        expression { return env.DEPLOY_URL?.trim() }
      }
      steps {
        // A green build that serves a blank page is worse than a red one.
        sh '''
          set -e
          for path in / /admin/login /app/profile; do
            code=$(curl -s -o /dev/null -w '%{http_code}' "$DEPLOY_URL$path")
            echo "$path -> $code"
            [ "$code" = "200" ] || { echo "FAILED: $path returned $code"; exit 1; }
          done
        '''
      }
    }
  }

  post {
    success {
      echo "OK  ${env.TARGET_BRANCH ?: 'build'}${env.DEPLOY_URL ? " — ${env.DEPLOY_URL}" : ' — built, not deployed'}"
    }
    failure {
      echo "FAILED  ${env.TARGET_BRANCH ?: 'build'} — see the stage log above."
    }
    always {
      // .vercel/ holds a pulled project link; never leave it in a shared workspace.
      dir(env.APP_DIR) {
        sh 'rm -rf .vercel .deploy-url || true'
      }
    }
  }
}
