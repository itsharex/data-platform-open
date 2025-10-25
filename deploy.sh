#!/bin/bash
set -e

# 定义颜色以提高可读性
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
GRAY='\033[0;90m'
NC='\033[0m' # 无颜色

# 日志函数
log_debug() { echo -e "${NC}$(date '+%Y-%m-%dT%H:%M:%S') ${BLUE}[DEBUG]${NC} ${GRAY}$1"; }
log_info() { echo -e "${NC}$(date '+%Y-%m-%dT%H:%M:%S') ${GREEN}[ INFO]${NC} $1"; }
log_warn() { echo -e "${NC}$(date '+%Y-%m-%dT%H:%M:%S') ${YELLOW}[ WARN]${NC} $1"; }
log_error() { echo -e "${NC}$(date '+%Y-%m-%dT%H:%M:%S') ${RED}[ERROR]${NC} $1"; exit 1; }

# 默认配置变量
COMPOSE_FILE="compose.yaml"
ENV_FILE=".env"
PASSWORD_FILE="password.txt"
IMAGE_MODE="pull"

DP_DOCKER_REGISTRY=""
DP_REGISTRY_NAMESPACE=""
DP_NAMESPACE="dp"
DP_IMAGE_TAG="latest"

# 包管理器
PACKAGE_MANAGER=""

# 本机 IP
HOST_IP=$(hostname -I | awk '{print $1}')

# NFS 地址
DP_NFS="${HOST_IP}"

# 显示帮助信息
show_help() {
  echo -e "${BLUE}Docker 部署脚本${NC}"
  echo "用法: $(basename "$0") [选项]"
  echo ""
  echo "选项:"
  echo "  -h, --help                    # 显示帮助信息"
  echo "  -m, --mode [pull|build]  # 镜像模式：pull 拉取仓库镜像，build 本地构建镜像 (默认: pull)"
  echo ""
  echo "示例:"
  echo "  $(basename "$0")              # 一键部署所有服务"
  echo "  $(basename "$0") -m build     # 本地构建镜像并且一键部署所有服务"
}

# 参数处理
parse_args() {
  WORK_DIR="$(pwd)"

  while [ $# -gt 0 ]; do
    case "$1" in
      -h|--help)
        show_help
        exit 0
        ;;
      -m|--mode)
        if [ -z "$2" ]; then
          log_error "参数 -m | --mode 需要一个值"
        fi
        case "$2" in
          pull|build)
            IMAGE_MODE="$2"
            shift 2
            ;;
          *)
            log_error "参数 -m | --mode 只能是 pull 或 build"
            ;;
        esac
        ;;
      *)
        log_error "未知参数: $1 \n 请使用 -h 或 --help 查看帮助信息"
        ;;
    esac
  done

}

# 检查 root 权限
check_root() {
  # 检查是否为 root 用户（安装软件通常需要 root 权限）
  if [ "$(id -u)" -ne 0 ]; then
    log_error "请以 root 用户或使用 sudo 权限运行此脚本！（sudo $(basename "$0")）"
  fi
}

# 检查操作系统
check_os() {
  log_info "检查操作系统..."
  local uname_s
  uname_s="$(uname -s 2>/dev/null || true)"
  if [ "$uname_s" != "Linux" ]; then
    log_error "此脚本仅支持 Linux 操作系统。"
  fi

  log_info "检查操作系统版本..."
  if [ -r /etc/os-release ]; then
    . /etc/os-release
  else
    log_error "无法检测操作系统（未找到 /etc/os-release 文件）"
  fi

  # 规范化与展示
  local id_lc id_like_lc ver
  id_lc="$(printf '%s' "${ID:-}" | tr '[:upper:]' '[:lower:]')"
  id_like_lc="$(printf '%s' "${ID_LIKE:-}" | tr '[:upper:]' '[:lower:]')"
  ver="${VERSION_ID:-unknown}"

  OS="${NAME:-$id_lc}"
  VERSION="$ver"
  log_info "检测到操作系统：${OS} ${VERSION}"

  # 选择包管理器
  local pm=""
  case ",$id_lc,$id_like_lc," in
    *,debian,*|*,ubuntu,*|*,linuxmint,*|*,kali,*|*,raspbian,*|*,deepin,*|*,uos,*)
      pm="apt"
      ;;
    *,rhel,*|*,centos,*|*,rocky,*|*,almalinux,*|*,ol,*|*,oracle,*|*,redhat,*)
      if command -v dnf >/dev/null 2>&1; then pm="dnf"; else pm="yum"; fi
      ;;
    *,fedora,*)
      pm="dnf"
      ;;
    *,sles,*|*,suse,*|*,opensuse,*)
      pm="zypper"
      ;;
    *,arch,*|*,manjaro,*|*,endeavouros,*)
      pm="pacman"
      ;;
    *,alpine,*)
      pm="apk"
      ;;
    *,amzn,*|*,amazon,*)
      if command -v dnf >/dev/null 2>&1; then pm="dnf"; else pm="yum"; fi
      ;;
    *,openeuler,*|*,euleros,*)
      if command -v dnf >/dev/null 2>&1; then pm="dnf"; else pm="yum"; fi
      ;;
    *)
      # 回退：按常见管理器检测
      for cand in apt dnf yum zypper pacman apk; do
        if command -v "$cand" >/dev/null 2>&1; then pm="$cand"; break; fi
      done
      ;;
  esac

  [ -z "$pm" ] && log_error "不支持的操作系统或未找到包管理器：ID=${id_lc} ID_LIKE=${id_like_lc}"
  if ! command -v "$pm" >/dev/null 2>&1; then
    log_error "${pm} 包管理器不可用，请检查系统配置！"
  fi

  PACKAGE_MANAGER="$pm"
  log_info "使用包管理器：${PACKAGE_MANAGER}"
}

# 检查 Docker 是否安装
check_docker() {
  log_info "检查 Docker 环境..."
  if ! command -v docker > /dev/null 2>&1; then
    log_warn "未找到 Docker 命令。请确保 Docker 已安装并添加到 PATH 中。
    可以使用以下命令安装 Docker：
    curl -fsSL https://get.docker.com | sudo sh
    参考 Docker 官方文档： https://docs.docker.com/engine/install/
    如果出现网络错误，可以尝试多次重试，或者手动安装 Docker。"
    # 交互式安装 Docker
    read -r -p "是否要安装 Docker？(Y/N，默认Y): " install_docker
    if [[ -z "$install_docker" || "$install_docker" =~ ^[Yy]$ ]]; then
      log_info "开始安装 Docker..."
      if command -v curl > /dev/null 2>&1; then
        curl -fsSL https://get.docker.com -o get-docker.sh
        sh get-docker.sh
        rm get-docker.sh
      else
        log_error "未找到 curl 命令，无法自动安装 Docker。请手动安装 Docker。
        参考 Docker 官方文档： https://docs.docker.com/engine/install/"
      fi
    else
      log_error "Docker 未安装，部署终止。请安装 Docker 后重试。
      参考 Docker 官方文档： https://docs.docker.com/engine/install/"
    fi
  fi

  log_debug "检查 Docker Swarm 模式..."
  if ! docker info | grep 'Swarm: active'; then
    log_info "Docker Swarm 未激活，正在初始化..."
    docker swarm init || log_error "初始化 Docker Swarm 失败！"
    log_debug "Docker Swarm 初始化成功"
  else
    log_debug "Docker Swarm 已激活"
  fi

  log_debug "Docker 环境检查通过"
}

# 提取 ${ENV_FILE} 文件中的变量
parse_env() {
  # 检查是否存在 ${ENV_FILE} 文件
  local env_file="docker/$ENV_FILE"

  # 设置默认值
  DP_DIR="/dp"

  # 如果 ${ENV_FILE} 文件存在，则读取变量
  if [ -f "$env_file" ]; then
    log_info "从 ${env_file} 文件中提取环境变量..."

    while IFS= read -r line || [ -n "$line" ]; do
      # 跳过空行注释
      [ -z "$line" ] || [[ $line == \#* ]] && continue

      # 提取键值对
      if [[ "$line" =~ ^([A-Za-z0-9_]+)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"

        # 移除引号
        value="${value%\"}"
        value="${value#\"}"
        value="${value%\'}"
        value="${value#\'}"

        # 设置全局变量
        declare -g "$key"="$value"
        log_debug "设置变量: $key=$value"
      fi
    done < "$env_file"

    log_debug "环境变量提取完成"
  else
    log_warn "文件 $env_file 不存在，使用默认值"
  fi

  # 输出使用的变量值
  log_info "变量值: DP_DIR=$DP_DIR"

  # 询问用户是否使用这些变量
  read -r -p "是否使用这些变量？(Y/N，默认Y): " use_vars
  if [[ -z "$use_vars" || "$use_vars" =~ ^[Yy]$ ]]; then
    log_info "使用变量: DP_DIR=${DP_DIR}"
  else
    log_error "用户选择不使用变量，部署终止。请调整 ${env_file} 文件中的变量后重试。"
  fi

}

# 设置密码函数
set_password() {
  # 优先从 ${DP_DIR}/conf/mysql/${PASSWORD_FILE} 或 docker/${ENV_FILE} 读取已存在的 MYSQL_PASSWORD
  if [ -z "$MYSQL_PASSWORD" ]; then
    if [ -f "${DP_DIR}/conf/mysql/${PASSWORD_FILE}" ]; then
      MYSQL_PASSWORD=$(cat "${DP_DIR}/conf/mysql/${PASSWORD_FILE}")
    elif [ -f "docker/${ENV_FILE}" ]; then
      MYSQL_PASSWORD=$(grep -E '^MYSQL_PASSWORD=' "docker/${ENV_FILE}" | cut -d'=' -f2-)
    fi
  fi

  # 设置 MySQL 密码
  if [ -z "$MYSQL_PASSWORD" ]; then
    log_info "随机生成 MySQL 密码，或者手动输入密码..."
    read -r -p "是否随机生成 MySQL 密码？(Y/N，默认Y): " generate_password
    if [[ -z "$generate_password" || "$generate_password" =~ ^[Yy]$ ]]; then
      MYSQL_PASSWORD=$(tr -dc '0-9A-Za-z_-' < /dev/urandom | head -c 8; echo)
      log_info "随机生成 MySQL 密码: ${MYSQL_PASSWORD}"
    else
      # 循环请求用户输入密码，直到满足要求
      while true; do
        read -r -p "请输入 MySQL 密码 (4-20位): " input_password
        if [ -z "$input_password" ]; then
          log_warn "MySQL 密码不能为空！请重新输入..."
        elif [ ${#input_password} -lt 4 ] || [ ${#input_password} -gt 20 ]; then
          log_warn "MySQL 密码长度必须在4-20位之间！请重新输入..."
        else
          MYSQL_PASSWORD="$input_password"
          log_info "使用用户输入的 MySQL 密码: ${MYSQL_PASSWORD}"
          break
        fi
      done
    fi
  else
    log_info "检测到已存在的 MySQL 密码: ${MYSQL_PASSWORD}"
  fi


  # 优先从 ${DP_DIR}/conf/redis/${PASSWORD_FILE} 或 docker/${ENV_FILE} 读取已存在的 REDIS_PASSWORD
  if [ -z "$REDIS_PASSWORD" ]; then
    if [ -f "${DP_DIR}/conf/redis/${PASSWORD_FILE}" ]; then
      REDIS_PASSWORD=$(cat "${DP_DIR}/conf/redis/${PASSWORD_FILE}")
    elif [ -f "docker/${ENV_FILE}" ]; then
      REDIS_PASSWORD=$(grep -E '^REDIS_PASSWORD=' "docker/${ENV_FILE}" | cut -d'=' -f2-)
    fi
  fi

  # 设置 Redis 密码
  if [ -z "$REDIS_PASSWORD" ]; then
    log_info "随机生成 Redis 密码，或者手动输入密码..."
    read -r -p "是否随机生成 Redis 密码？(Y/N，默认Y): " generate_password
    if [[ -z "$generate_password" || "$generate_password" =~ ^[Yy]$ ]]; then
      REDIS_PASSWORD=$(tr -dc '0-9A-Za-z_-' < /dev/urandom | head -c 8; echo)
      log_info "随机生成 Redis 密码: ${REDIS_PASSWORD}"
    else
      # 循环请求用户输入密码，直到满足要求
      while true; do
        read -r -p "请输入 Redis 密码 (4-20位): " input_password
        if [ -z "$input_password" ]; then
          log_warn "Redis 密码不能为空！请重新输入..."
        elif [ ${#input_password} -lt 4 ] || [ ${#input_password} -gt 20 ]; then
          log_warn "Redis 密码长度必须在4-20位之间！请重新输入..."
        else
          REDIS_PASSWORD="$input_password"
          log_info "使用用户输入的 Redis 密码: ${REDIS_PASSWORD}"
          break
        fi
      done
    fi
  else
    log_info "检测到已存在的 Redis 密码: ${REDIS_PASSWORD}"
  fi

}

# 创建目录函数
create_dir() {
  log_info "准备创建目录并赋予权限..."

  log_info "准备创建 conf 目录..."
  local conf_dirs=(
    "${DP_DIR}/conf"
  )

  for i in "${!conf_dirs[@]}"; do
    if [ ! -d "${conf_dirs[$i]}" ]; then
      log_info "创建 ${conf_dirs[$i]} 目录..."
      mkdir -p "${conf_dirs[$i]}" || log_error "创建 ${conf_dirs[$i]} 目录失败！"
      chmod 755 "${conf_dirs[$i]}" || log_warn "设置 ${conf_dirs[$i]} 目录权限失败！"
      log_debug "${conf_dirs[$i]} 目录创建成功！"
    else
      log_debug "${conf_dirs[$i]} 目录已存在，跳过..."
    fi
  done

  # 将 docker 目录中的所有文件，包括子目录和隐藏文件，复制到 ${DP_DIR}/conf
  log_info "复制 docker 目录中的所有文件到 ${DP_DIR}/conf ..."
  cp -R "${WORK_DIR}/docker/"* "${DP_DIR}/conf/" || log_error "复制 docker 目录文件失败！"
  log_debug "docker 目录文件复制成功！"

  # 必须给 .erlang.cookie 文件设置合适的权限，否则 rabbitmq 无法启动。
  log_info "设置 ${DP_DIR}/conf/rabbitmq/.erlang.cookie 文件权限..."
  chmod 400 "${DP_DIR}/conf/rabbitmq/.erlang.cookie" || log_error "设置 ${DP_DIR}/conf/rabbitmq/.erlang.cookie 文件权限失败！"
  chown 999:999 "${DP_DIR}/conf/rabbitmq/.erlang.cookie" || log_error "设置 ${DP_DIR}/conf/rabbitmq/.erlang.cookie 文件权限失败！"
  log_debug "${DP_DIR}/conf/rabbitmq/.erlang.cookie 文件权限设置成功！"

  log_info "准备创建 data 目录..."
  local data_dirs=(
    "${DP_DIR}/data"
    "${DP_DIR}/data/mysql"
    "${DP_DIR}/data/redis"
    "${DP_DIR}/data/rabbitmq"
    "${DP_DIR}/data/grafana"
    "${DP_DIR}/data/prometheus"
    "${DP_DIR}/data/loki"
    "${DP_DIR}/data/tempo"
    "${DP_DIR}/data/alloy"
  )

  for i in "${!data_dirs[@]}"; do
    if [ ! -d "${data_dirs[$i]}" ]; then
      log_info "创建 ${data_dirs[$i]} 目录..."
      mkdir -p "${data_dirs[$i]}" || log_error "创建 ${data_dirs[$i]} 目录失败！"
      chmod 755 "${data_dirs[$i]}" || log_warn "设置 ${data_dirs[$i]} 目录权限失败！"
      log_debug "${data_dirs[$i]} 目录创建成功！"
    else
      log_debug "${data_dirs[$i]} 目录已存在，跳过..."
    fi
  done

  log_info "准备创建 logs 目录..."
  local log_dirs=(
    "${DP_DIR}/logs"
    "${DP_DIR}/logs/data-platform-web"
    "${DP_DIR}/logs/data-platform-flow"
    "${DP_DIR}/logs/data-platform-query"
    "${DP_DIR}/logs/data-platform-support"
  )

  for i in "${!log_dirs[@]}"; do
    if [ ! -d "${log_dirs[$i]}" ]; then
      log_info "创建 ${log_dirs[$i]} 目录..."
      mkdir -p "${log_dirs[$i]}" || log_error "创建 ${log_dirs[$i]} 目录失败！"
      chmod 777 "${log_dirs[$i]}" || log_warn "设置 ${log_dirs[$i]} 目录权限失败！"
      log_debug "${log_dirs[$i]} 目录创建成功！"
    else
      log_debug "${log_dirs[$i]} 目录已存在，跳过..."
    fi
  done

}

# 判断包是否已安装
is_pkg_installed() {
  local pkg="$1"
  case "$PACKAGE_MANAGER" in
    apt)
      dpkg -s "$pkg" >/dev/null 2>&1
      ;;
    dnf|yum|zypper)
      rpm -q "$pkg" >/dev/null 2>&1
      ;;
    pacman)
      pacman -Qi "$pkg" >/dev/null 2>&1
      ;;
    apk)
      apk info -e "$pkg" >/dev/null 2>&1
      ;;
    *)
      return 1
      ;;
  esac
}

# 安装包
install_pkg() {
  local pkg="$1"
  log_info "使用 ${PACKAGE_MANAGER} 安装包: ${pkg} ..."
  case "$PACKAGE_MANAGER" in
    apt)
      apt-get update -y || log_warn "apt-get update 失败，继续尝试安装..."
      apt-get install -y "$pkg" || return 1
      ;;
    dnf)
      dnf install -y "$pkg" || return 1
      ;;
    yum)
      yum install -y "$pkg" || return 1
      ;;
    zypper)
      zypper --non-interactive install -y "$pkg" || return 1
      ;;
    pacman)
      pacman -Sy --noconfirm "$pkg" || return 1
      ;;
    apk)
      apk add --no-cache "$pkg" || return 1
      ;;
    *)
      return 1
      ;;
  esac
  return 0
}

# 创建 NFS
create_nfs() {
  log_info "检查 NFS 服务..."

  local svc_candidates=(nfs-server nfs-kernel-server nfs)
  local pkg exports_file opts found_svc s backup_ts esc_dir existing new_line

  # 选择包名
  if [ "$PACKAGE_MANAGER" = "apt" ]; then
    pkg="nfs-kernel-server"
  else
    pkg="nfs-utils"
  fi

  # 确保已安装
  if ! is_pkg_installed "$pkg"; then
    log_info "安装 ${pkg} ..."
    install_pkg "$pkg" || log_error "安装包 ${pkg} 失败，请手动检查系统包管理器或网络"
  else
    log_debug "包 ${pkg} 已安装"
  fi

  # 启动服务（systemd 优先）
  found_svc=""
  if command -v systemctl >/dev/null 2>&1; then
    for s in "${svc_candidates[@]}"; do
      if systemctl list-unit-files --type=service | grep -Eq "^${s}\.service"; then
        found_svc="$s"
        break
      fi
    done
    if [ -n "$found_svc" ]; then
      if systemctl is-active --quiet "$found_svc"; then
        log_debug "NFS 服务 ${found_svc} 已在运行"
      else
        log_info "启动 NFS 服务 ${found_svc} ..."
        systemctl enable --now "$found_svc" >/dev/null 2>&1 \
          && log_info "NFS 服务 ${found_svc} 启动成功" \
          || log_warn "NFS 服务 ${found_svc} 启动失败，请稍后检查"
      fi
    else
      log_warn "未发现已知的 NFS 服务单元（nfs-server/nfs-kernel-server/nfs），继续处理导出"
    fi
  else
    # 兼容非 systemd 系统
    if command -v service >/dev/null 2>&1; then
      for s in "${svc_candidates[@]}"; do
        if service "$s" start >/dev/null 2>&1; then
          log_info "通过 service 启动 NFS 服务 ${s} 成功"
          found_svc="$s"
          break
        fi
      done
    fi
    [ -z "$found_svc" ] && log_warn "无法通过传统方式确认或启动 NFS 服务，继续处理导出"
  fi

  # 确保导出目录存在
  [ -d "$DP_DIR" ] || { mkdir -p "$DP_DIR" || log_error "创建目录 ${DP_DIR} 失败"; chmod 755 "$DP_DIR" || true; }

  # 配置 /etc/exports（幂等）
  exports_file="/etc/exports"
  opts="rw,sync,no_subtree_check,no_root_squash"
  backup_ts=$(date +%s)
  [ -f "$exports_file" ] && cp "$exports_file" "${exports_file}.bak.${backup_ts}" 2>/dev/null || true

  # 转义路径用于正则
  esc_dir=$(printf '%s' "$DP_DIR" | sed -e 's/[].[^$\\*/]/\\&/g')
  existing=$(grep -E "^[[:space:]]*${esc_dir}\b" "$exports_file" 2>/dev/null || true)
  new_line="${DP_DIR} ${HOST_IP}(${opts}) 127.0.0.1(${opts}) ::1(${opts})"

  if [ -n "$existing" ]; then
    if printf '%s\n' "$existing" | grep -Eq "(\b${HOST_IP}\b|127\.0\.0\.1|\blocalhost\b|::1)"; then
      log_debug "已存在对 ${DP_DIR} 的导出且包含本机访问，跳过追加"
    else
      if ! grep -Fxq "$new_line" "$exports_file" 2>/dev/null; then
        echo "$new_line" >> "$exports_file" || log_error "写入 ${exports_file} 失败"
        log_info "已为 ${DP_DIR} 追加允许本机访问的导出"
      fi
    fi
  else
    if ! grep -Fxq "$new_line" "$exports_file" 2>/dev/null; then
      echo "$new_line" >> "$exports_file" || log_error "写入 ${exports_file} 失败"
      log_info "已为 ${DP_DIR} 添加导出"
    fi
  fi

  # 刷新导出（无需重启服务）
  if command -v exportfs >/dev/null 2>&1; then
    exportfs -r >/dev/null 2>&1 || exportfs -a >/dev/null 2>&1 || log_error "exportfs 刷新导出失败"
    log_info "NFS 导出已刷新生效"
  else
    log_error "未找到 exportfs 命令，请确认 NFS 工具已正确安装"
  fi

}

# 创建网络函数
create_network() {
  log_info "检查 Docker 网络 ${DP_NAMESPACE} ..."
  if docker network inspect "${DP_NAMESPACE}" &>/dev/null; then
    log_debug "Docker 网络 ${DP_NAMESPACE} 已存在"
  else
    log_info "创建 Docker 网络 ${DP_NAMESPACE} ..."
    docker network create --driver overlay "${DP_NAMESPACE}" || log_error "创建 Docker 网络 ${DP_NAMESPACE} 失败！"
    log_debug "创建 Docker 网络 ${DP_NAMESPACE} 成功！"
  fi
}

# 准备镜像函数
prepare_image() {
  # 询问用户选择镜像模式
  log_info "请输入选项数字以选择获取镜像的方式（直接回车默认选择 pull 拉取仓库镜像）:"
  log_warn "如果当前是无网环境，请选择 skip 跳过构建镜像，同时，请准备好了离线的 docker 镜像，并且调整好各个服务的 ${COMPOSE_FILE} 配置文件中的镜像名称"
  while true; do
    echo "1) pull 拉取仓库镜像"
    echo "2) build 本地构建镜像"
    read -r -p "请选择选项 (直接回车默认为1): " choice

    if [[ -z "$choice" || "$choice" == "1" ]]; then
      IMAGE_MODE="pull"
      log_info "您选择了拉取仓库镜像..."
      break
    elif [[ "$choice" == "2" ]]; then
      IMAGE_MODE="build"
      log_info "您选择了本地构建镜像..."
      break
    else
      log_warn "无效的选项 $choice，请重新选择..."
    fi
  done

  images=(
    "data-platform-web"
    "data-platform-flow"
    # "data-platform-query"
    "data-platform-support"
    "data-platform-front"
    )
  # 遍历镜像列表
  for image in "${images[@]}"; do
    if [ "$IMAGE_MODE" = "pull" ]; then
      pull_image "${image}"
    elif [ "$IMAGE_MODE" = "build" ]; then
      build_image "${image}"
    fi
  done
}

# 拉取镜像函数
pull_image() {
  local image_name="$1";
  log_info "开始拉取 Docker 镜像 ${image_name} ..."
  # 检查 Docker Registry 是否存在
  if [ -z "${DP_DOCKER_REGISTRY}" ]; then
    log_error "Docker Registry 未设置，请检查 ${ENV_FILE} 文件中的配置！"
  fi
  if [ -z "${DP_REGISTRY_NAMESPACE}" ]; then
    log_error "Registry Namespace 未设置，请检查 ${ENV_FILE} 文件中的配置！"
  fi
  # 如果 DP_REGISTRY_NAMESPACE = "shaiwz-public"，则不需要登录
  if [ "${DP_REGISTRY_NAMESPACE}" = "shaiwz-public" ]; then
    log_debug "Registry Namespace 为 shaiwz-public，无需登录！"
  else
    # 尝试登录 Docker Registry
    log_info "尝试登录 Docker Registry：${DP_DOCKER_REGISTRY} ..."
    docker login "${DP_DOCKER_REGISTRY}"
  fi
  # 拉取 Docker 镜像
  log_info "拉取 Docker 镜像 ${image_name} ..."
  docker pull "${DP_DOCKER_REGISTRY}/${DP_REGISTRY_NAMESPACE}/${image_name}:${DP_IMAGE_TAG}" || log_error "拉取 Docker 镜像 ${image_name} 失败！"
  log_debug "拉取 Docker 镜像 ${image_name} 成功！"
  # docker tag "${DP_DOCKER_REGISTRY}/${DP_REGISTRY_NAMESPACE}/${image_name}:${DP_IMAGE_TAG}" "${image_name}" || log_error "标记 Docker 镜像 ${image_name} 失败！"
  # log_debug "标记 Docker 镜像 ${image_name} 成功！"
}

# 构建镜像函数
build_image() {
  local image_name="$1";
  chmod +x "${WORK_DIR}/build.sh" || log_warn "设置 build.sh 文件权限失败！"
  "${WORK_DIR}/build.sh" "${image_name}" "${DP_IMAGE_TAG}" || log_error "镜像构建也失败了！"
}

# Docker 部署
docker_deploy() {

  log_debug "替换 ${COMPOSE_FILE} 文件中的变量..."
  sed -i "s|\${DP_DIR}|${DP_DIR}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 DP_DIR 变量失败！"
  sed -i "s|\${DP_DOCKER_REGISTRY}|${DP_DOCKER_REGISTRY}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 DP_DOCKER_REGISTRY 变量失败！"
  sed -i "s|\${DP_REGISTRY_NAMESPACE}|${DP_REGISTRY_NAMESPACE}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 DP_REGISTRY_NAMESPACE 变量失败！"
  sed -i "s|\${DP_IMAGE_TAG}|${DP_IMAGE_TAG}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 DP_IMAGE_TAG 变量失败！"
  sed -i "s|.*|${MYSQL_PASSWORD}|g" "${DP_DIR}/conf/mysql/${PASSWORD_FILE}" || log_error "替换 MYSQL_PASSWORD 变量失败！"
  sed -i "s|.*|${REDIS_PASSWORD}|g" "${DP_DIR}/conf/redis/${PASSWORD_FILE}" || log_error "替换 REDIS_PASSWORD 变量失败！"
  sed -i "s|\${MYSQL_PASSWORD}|${MYSQL_PASSWORD}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 MYSQL_PASSWORD 变量失败！"
  sed -i "s|\${REDIS_PASSWORD}|${REDIS_PASSWORD}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 REDIS_PASSWORD 变量失败！"
  sed -i "s|\${DP_NFS}|${DP_NFS}|g" "${DP_DIR}/conf/${COMPOSE_FILE}" || log_error "替换 DP_NFS 变量失败！"
  log_debug "${COMPOSE_FILE} 文件中的变量替换成功！"

  log_info "准备进行 Docker 部署..."

  docker stack deploy -c "${DP_DIR}/conf/${COMPOSE_FILE}" "${DP_NAMESPACE}" || log_error "Docker Stack 部署失败！"
}

# 检查容器健康状态
service_health_check() {
  local retries=18
  local seconds=10
  local count=0
  local health=false

  local service="$1";
  log_info "准备检查服务健康状态 ${service} ..."

  while [ ${count} -lt ${retries} ]; do
    for container in $(docker ps -q --filter "name=${service}"); do
      local status
      status=$(docker inspect --format '{{.State.Health.Status}}' "${container}")
      if [ "${status}" == "healthy" ]; then
        health=true
      else
        health=false
      fi
    done
    if [ "${health}" == "true" ]; then
      log_info "服务 ${service} 健康检查成功！"
      return 0
    fi
    log_warn "服务 ${service} 健康检查失败，等待 ${seconds} 秒后重试..."
    sleep ${seconds}
    count=$((count + 1))
  done

  log_error "服务 ${service} 健康检查失败，超出最大重试次数"
}

# 主函数
main() {
  # 确保我们在正确的目录中（脚本所在目录）
  cd "$(dirname "$0")" || log_error "无法切换到脚本目录"

  # 检查 root 权限
  check_root

  # 检查系统环境
  check_os

  # 检查 Docker 环境
  check_docker

  # 提取环境变量
  parse_env

  # 解析命令行参数
  parse_args "$@"

  # 设置密码
  set_password

  # 创建目录
  create_dir

  # 创建 nfs
  create_nfs

  # 创建网络
  # create_network

  # 准备镜像
  prepare_image

  # 部署
  docker_deploy

  # 切换到 conf 目录
  log_debug "切换到 ${DP_DIR}/conf/ 目录..."
  cd "${DP_DIR}/conf/" || log_error "切换到 ${DP_DIR}/conf/ 目录失败"

  log_info "部署成功！！！"
  log_info "查看容器状态： docker service ls"

  log_info "尝试访问网站： http://${HOST_IP}:80"

}

# 执行主函数
main "$@"

/bin/bash
