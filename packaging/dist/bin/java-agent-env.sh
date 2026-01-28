#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_AGENT_HOME="${JAVA_AGENT_HOME:-${TOOLBOX_HOME:-$(cd "${script_dir}/.." && pwd)}}"
export JAVA_AGENT_HOME
export TOOLBOX_HOME="${JAVA_AGENT_HOME}"

config_file="${JAVA_AGENT_HOME}/conf/java-agent.conf"
if [ ! -f "${config_file}" ]; then
  echo "Config file not found: ${config_file}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "${config_file}"
set +a

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME is required in conf/java-agent.conf" >&2
  exit 1
fi

if [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "JAVA_HOME invalid or java not executable: ${JAVA_HOME}/bin/java" >&2
  exit 1
fi

export PATH="${JAVA_HOME}/bin:${PATH}"

resolve_path() {
  local path="$1"
  if [ -z "${path}" ]; then
    return 0
  fi
  case "${path}" in
    /*)
      printf '%s' "${path}"
      ;;
    ~/*)
      printf '%s' "${HOME:-}/${path#~/}"
      ;;
    [A-Za-z]:[\\/]*)
      printf '%s' "${path}"
      ;;
    *)
      printf '%s' "${JAVA_AGENT_HOME}/${path}"
      ;;
  esac
}

trim_value() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

resolve_path_list() {
  local list="$1"
  if [ -z "${list}" ]; then
    return 0
  fi
  local result=()
  local IFS=','
  read -r -a items <<< "${list}"
  for item in "${items[@]}"; do
    local trimmed
    trimmed="$(trim_value "${item}")"
    if [ -n "${trimmed}" ]; then
      result+=("$(resolve_path "${trimmed}")")
    fi
  done
  local joined
  joined="$(IFS=','; printf '%s' "${result[*]}")"
  printf '%s' "${joined}"
}

if [ -z "${JAVA_AGENT_CONFIG:-}" ]; then
  if [ -n "${TOOLBOX_CONFIG:-}" ]; then
    JAVA_AGENT_CONFIG="${TOOLBOX_CONFIG}"
  else
    JAVA_AGENT_CONFIG="${JAVA_AGENT_HOME}/conf/application.yml"
  fi
else
  JAVA_AGENT_CONFIG="$(resolve_path "${JAVA_AGENT_CONFIG}")"
fi

if [ ! -f "${JAVA_AGENT_CONFIG}" ]; then
  echo "Application config not found: ${JAVA_AGENT_CONFIG}" >&2
  exit 1
fi

if [ -z "${JAVA_AGENT_TOOLSFILES:-}" ] && [ -n "${TOOLBOX_TOOLSFILES:-}" ]; then
  JAVA_AGENT_TOOLSFILES="${TOOLBOX_TOOLSFILES}"
fi

if [ -z "${JAVA_AGENT_TOOLSFILES:-}" ] && [ -n "${TOOLBOX_TOOLSFILE:-}" ]; then
  JAVA_AGENT_TOOLSFILES="${TOOLBOX_TOOLSFILE}"
fi

if [ -n "${JAVA_AGENT_TOOLSFILES:-}" ]; then
  JAVA_AGENT_TOOLSFILES="$(resolve_path_list "${JAVA_AGENT_TOOLSFILES}")"
fi

export JAVA_AGENT_CONFIG
export JAVA_OPTS="${JAVA_OPTS:-}"
APP_OPTS="${APP_OPTS:-}"

append_app_opt() {
  local key="$1"
  local value="$2"
  if [ -n "${value}" ]; then
    case " ${APP_OPTS} " in
      *" --${key}="*)
        return
        ;;
    esac
    if [ -z "${APP_OPTS}" ]; then
      APP_OPTS="--${key}=${value}"
    else
      APP_OPTS="${APP_OPTS} --${key}=${value}"
    fi
  fi
}

append_app_opt "server.port" "${SERVER_PORT:-}"
append_app_opt "upload.url" "${UPLOAD_URL:-}"
if [ -n "${JAVA_AGENT_TOOLSFILES:-}" ]; then
  append_app_opt "java-agent.toolsFiles" "${JAVA_AGENT_TOOLSFILES}"
fi

export APP_OPTS
