# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

Kubernetes 기반 MSA 환경에서 Observability 플랫폼을 구축하는 포트폴리오 프로젝트.
장애 시뮬레이션 → 탐지 → 대응 → 분석 자동화를 목표로 한다.

## Tech Stack

- Language: Java 17
- Framework: Spring Boot 3.x, Spring Web, Spring Data JPA
- Build: Maven
- Infrastructure: Kubernetes (kubeadm, 싱글노드), ArgoCD
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
```

## Git Conventions

- 커밋 메시지: `type: 설명` (예: `feat: 주문 API 구현`, `fix: 메트릭 수집 누락 수정`)
- type: feat, fix, refactor, docs, infra, test, chore
- 브랜치: `feature/{기능명}`, `fix/{버그명}`

## Current Status

- 1단계 완료 ✅ (order-service 개발 및 배포, Prometheus/Grafana 메트릭 연동)
- 2단계 완료 ✅ (payment/notification-service 개발, 분산 트레이싱 연동)
- 3단계 완료 ✅ (SLO 대시보드, JVM 대시보드, Alert Rule 작성 및 클러스터 적용)
- 현재: 4단계 진행 예정 (k6 부하 테스트)
- 상세 계획: `docs/PLAN.md` 참조

## Project Progress

- 프로젝트의 단계별 진행 상황은 `docs/PLAN.md`에서 관리한다.
- 작업 시작 전 `docs/PLAN.md`를 참조하여 현재 단계와 완료 여부를 확인한다.
- 각 단계의 할 일 목록과 완료 기준을 숙지하고 작업한다.

## Important Rules

- infra/ 디렉토리의 YAML 파일을 수정할 때는 들여쓰기 2칸을 유지한다.
- Helm values.yaml 수정 시 기존 주석을 삭제하지 않는다.
- sample-apps의 각 서비스는 독립적으로 빌드/배포 가능해야 한다.
- 환경변수는 하드코딩하지 않고 application.yml 또는 K8s ConfigMap/Secret으로 관리한다.
