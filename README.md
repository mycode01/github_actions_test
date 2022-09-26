travis 를 이용한 cicd를 테스트해보고싶었음.    
bitbucket에서의 배포는 작업을 yaml로 스크립트를 작성하여     
aws에 빌드된 이미지를 업로드      
예약 인스턴스나 fargate로 해당 이미지로 컨테이너 생성을 하는 방식이었는데, 

지금 진행중인 토이프로젝트는 금전적인 문제로   
프리티어 ec2 인스턴스에 그냥 jar파일을 업로드하여 java -jar 로 실행하는 방식을 택했음.

그러다보니 cicd에 대한 부분을 테스트해보고자 이야기만 들었던 travis ci 를 이용하려고 봤더니
travis는 철저히 ci만 해주고 cd는 해주지 않아서 aws codedeploy를 이용해야 했음. 

간단한 작업인데 굳이 travis 플러그인에 aws의 다른 기능에 의존하는게 싫어서     
github actions 만을 이용해서 빌드/배포까지를 테스트해보았고,   
이 레파지토리는 단순한 ping api만 제공하는 소스코드를 담고있음.

마침 aws의 t4g.small 이하는 2022년 12월 말일까지 무료라서 새로 가입후에 이용해보았음.    
아마존리눅스2를 이용하였고, jdk가 설치되어있지 않아 아래의 명령으로 별도 설치하였음.
```shell
sudo amazon-linux-extras install java-openjdk11
```
github의 actions 를 이용하려면 레파지토리에 .github/workflow 라는 디렉토리를 생성후에     
원하는 작업 스크립트를 yml로 작성해야한다.    
물론 actions 탭에 들어가면 알려주지만 이미 작성된 스크립트들이 marketplace에 있기 때문에    
적절한 워크플로를 선택후에 디테일만 수정해서 커밋하게되면 actions 사용이 가능하다.


나는 java를 maven으로 빌드하여 나온 jar파일을 ec2인스턴스에 업로드하고,    
해당 jar파일을 구동시키는 작업을 진행하였다.    
추천으로 java ci with maven 이 있어서 해당 템플릿을 선택하였음.


```yaml
on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]
```
언제 해당 워크플로가 동작해야하는지를 정의한다.    
branch master 에 push 되었을때, 혹은 pr이 발생하였을때 동작한다.


```yaml
jobs:
  build:
    runs-on: ubuntu-latest
```
워크 플로 jobs 정의 부분     
어떤 환경에서 스크립트가 동작할지를 정의한다.     
써있다 시피 build라는 job 이름으로 ubuntu-lastest 에서 동작한다.    
기본적으로 제공하는 os이 몇가지 있으니 본인의 빌드 환경에 따라 선택하면 될거같다. 
```yaml
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
        cache: maven
```
워크플로는 jobs로 나뉘고 jobs는 다시한번 steps로 나뉜다.    
jobs는 완전히 별개의 작업으로 나뉘고, steps는 그 jobs안에서 순차적으로 실행되는 환경이다.    
작업 순서를 제어하고싶다면 needs 속성을 이용하여 먼저 선행되어야 하는 step을 지정할수 있긴한데..    
테스트해본결과 어차피 순차적으로 동작하여 복잡한 작업이 아니라면 그냥 순차적으로만 적어줘도 될거같다.    

암튼 첫번째 스탭은 
actions/checkout@v3 라는 미리 작성된 액션을 이용한다 정의 하고 있다.    
말 그대로 해당 브랜치를 checkout 해온다.

다음 스탭은 set up jdk 11 이라는 이름을 지정한 스탭이며,    
action/setup-java@v3 를 이용한다. 해당 액션은 파라메터가 몇개 필요하며 with 키워드로 정의해준다.

```yaml
    - name: replace config string
      run: |
          sed -i "s/somemsgstring/HELLo world/g" src/main/resources/application-prod.properties 
          sed -i "s/HELLo world/hello world!/g" src/main/resources/application-prod.properties
```
아무래도 public 브랜치이고, docker 이미지를 빌드하지 않는 환경이다 보니까 환경변수를 설정할 부분이 마땅치 않아 넣어준 스크립트이다.
단순히 빌드직전 application-prod.properties 에서 지정된 스트링을 바꿔주기만 하는 스탭이다.

```yaml
    - name: Build with Maven
      run: mvn -B package --file pom.xml -DskipTests
```
실제 maven 빌드를 실행시키는 스탭이다.    
테스트는 스프링 부트 기본 테스트가 포함되어있어서, 이를 빌드타임에 제외하는 플래그를 추가하였다.

```yaml
    - name: Stop application
      uses: garygrossgarten/github-action-ssh@release
      with:
        command: kill -9 `pgrep -f ping-0.0.1-SNAPSHOT.jar`
        host: ${{ secrets.DEV_HOST }}
        username: ec2-user
        privateKey: ${{ secrets.DEV_PEM_KEYPAIR}}
      env:
        CI: true
```
배포단계에서 현재 ec2인스턴스에 올라가있는 작업을 중지하는 스크립트이다.    
원래는 appleboy/ssh-action 액션을 사용했었는데,    
무슨문제인지 kill 명령에 대해 처리는 되는데 리턴이 에러로 리턴되어 뒤에 남은 스탭이 전부 취소되는 문제가 있어서 해당 액션을 사용하였음.    
몇가지 파라메터가 필요하여 with로 정의해주었고,     
${{}} 리터럴은 레파지토리 세팅 > secrets 에 선행 등록된 스트링을 가져올수있도록 하였다.

```yaml
    - name: Publish jar
      uses: nogsantos/scp-deploy@master
      with:
        src: ./target/ping-0.0.1-SNAPSHOT.jar
        host: ${{ secrets.DEV_HOST }}
        remote: .
        port: 22
        user: ec2-user
        key: ${{ secrets.DEV_PEM_KEYPAIR }}
```
scp를 이용하여 빌드된 아티팩트를 리모트에 업로드하는 액션을 사용하였다.    
nogsantos/scp-deploy@master 액션을 사용하였음.    
src는 maven 빌드가 완료되었을때 빌드 결과물을 선택하였다.    

```yaml
    - name: Start application
      uses: appleboy/ssh-action@dce9d565de8d876c11d93fa4fe677c0285a66d78
      with:
        host: ${{ secrets.DEV_HOST }}
        port: 22
        username: ec2-user
        key: ${{ secrets.DEV_PEM_KEYPAIR }}
        script: |
            chmod 755 ping-0.0.1-SNAPSHOT.jar
            nohup java -jar ping-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > /dev/null &
```
마지막으로 업로드 된 결과물을 실행시키는 액션이다.    
가장 인기 좋은 ssh 액션인듯 한데 latest 버전에서는 빈 줄이 몇십개 로깅되는 버그가 있어서 해당 버전으로 선택함    
실행후 바로 연결이 끊기기 때문에 nohup 으로 실행하였고, 로그는 남기지 않는다.

이 모든 작업을 그냥 본인이 스크립트로 runs 로 작성할수도 있지만     
해당 작업들이 다 잘 돌아가는지 거의 시뮬레이트하듯이 작성을 하고싶지는 않았기 때문에 미리 작성된 action 들을 주로 사용하였음.
