# bootstrap 원격 state (KAN-245, 2026-10-03). 처음에는 로컬 state였다 - state를 담을 버킷을 이 스택이 만들기
# 때문이다(닭과 달걀). 버킷이 생긴 뒤에는 그 버킷에 둬도 된다: 버전 관리, 암호화, TLS 강제, prevent_destroy가
# 걸려 있고, 로컬 파일은 한 사람의 Mac에만 있어 다른 팀원이 이 스택을 관리할 수 없었다.
# 버킷을 처음부터 다시 만들어야 하는 날(계정 이전 등)에는 이 파일을 잠시 빼고 로컬 state로 apply한 뒤
# terraform init -migrate-state로 되돌린다.
terraform {
  backend "s3" {
    bucket       = "accentury-tfstate-325771561913"
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
