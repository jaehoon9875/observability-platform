# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

Kubernetes 기반 MSA 환경에서 Observability 플랫폼을 구축하는 포트폴리오 프로젝트.
장애 시뮬레이션 → 탐지 → 대응 → 분석 자동화를 목표로 한다.

## Tech Stack

- Language: Java 17
- Framework: Spring Boot 3.x, Spring Web, Spring Data JPA
- Build: Maven
- Infrastructure: Kubernetes (kubeadm, 싱글노드), ArgoCD, GitHub Actions
- Observability: kube-prometheus-stack, Grafana, Loki, Alloy, Tempo
- Database: MySQL, Redis
- Messaging: Kafka
- Load Testing: k6
- Scripting: Python, Shell Script

## Project Structure

```
sample-apps/       → MSA 애플리케이션 (order, payment, notification)
custom-exporter/   → 커스텀 Prometheus Exporter
infra/             → Helm values, ArgoCD manifests, K8s manifests
dashboards/        → Grafana 대시보드 JSON
alerts/            → Prometheus Alert Rule YAML
tests/             → k6 부하 테스트 시나리오 (load-test/, spike-test/)
scripts/           → 운영 자동화 스크립트
docs/              → 문서
```

## Git Conventions

- 커밋 메시지: `type: 설명` (예: `feat: 주문 API 구현`, `fix: 메트릭 수집 누락 수정`)
- type: feat, fix, refactor, docs, infra, test, chore
- 브랜치: `feature/{기능명}`, `fix/{버그명}`

## Current Status

현재 **9단계 (전체 리뷰 및 미해결 이슈 개선)** 진행 중. 1~8단계 완료.
- 단계별 상세 내용 및 진행 현황 → `docs/PLAN.md` 참조
- 미해결 이슈 및 개선 항목 → `docs/ISSUES.md` 참조

## Important Rules

- infra/ 디렉토리의 YAML 파일을 수정할 때는 들여쓰기 2칸을 유지한다.
- Helm values.yaml 수정 시 기존 주석을 삭제하지 않는다.
- sample-apps의 각 서비스는 독립적으로 빌드/배포 가능해야 한다.
- 환경변수는 하드코딩하지 않고 application.yml 또는 K8s ConfigMap/Secret으로 관리한다.
- ArgoCD로 관리되는 리소스는 `helm upgrade` CLI나 `kubectl apply`로 직접 수정하지 않는다.
  직접 수정하면 SSA(Server-Side Apply) 필드 소유권이 분리되어, ArgoCD가 해당 필드를 관리하지 못하는 drift가 발생한다.
  설정 변경은 반드시 infra/ 파일을 수정하고 Git push → ArgoCD sync 경로로만 반영한다.
