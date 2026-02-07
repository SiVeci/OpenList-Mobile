# 📂 OpenList Mobile

> **The Ultimate Mobile-First AList Client.**  
> **极致体验的 AList 移动端三方客户端。**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Mobile%20Web-orange.svg)](#)
[![Tech Stack](https://img.shields.io/badge/tech-React%20%2B%20Tailwind-61dafb.svg)](#)

---

## 🌟 Introduction / 简介

**OpenList Mobile** 是一款专为移动端优化的 AList 网页客户端。它不仅拥有基于 **Material Design 3** 的精美交互界面，还深度整合了多服务器管理、流媒体播放和文件预览功能，让你在手机上管理个人云端存储变得如丝般顺滑。

**OpenList Mobile** is a sleek, high-performance web client for AList, specifically optimized for mobile devices. Built with **Material Design 3** principles, it provides a seamless experience for managing multi-server storage, streaming media, and previewing documents on the go.

---

## 📸 Screenshots / 界面展示

| **Login & History** | **File Browser** | **Action Sheet** |
| :---: | :---: | :---: |
| ![Login UI](https://images.unsplash.com/photo-1555774698-0b77e0d5fac6?q=80&w=300&h=600&auto=format&fit=crop) | ![Browser UI](https://images.unsplash.com/photo-1614332287897-cdc485fa562d?q=80&w=300&h=600&auto=format&fit=crop) | ![Preview UI](https://images.unsplash.com/photo-1512428559087-560fa5ceab42?q=80&w=300&h=600&auto=format&fit=crop) |
| *Modern Login with History* | *Fluid File Navigation* | *Smart Actions & Preview* |

---

## ✨ Key Features / 功能特性

### 🚀 Performance & UI / 性能与交互
- **Mobile First Design:** 完全针对移动端手势优化，支持左侧边缘滑动返回。  
  **Mobile First Design:** Fully optimized for mobile gestures with edge-swipe back support.
- **Material Design 3:** 现代化的色彩体系与圆角设计，流畅的触控反馈。  
  **Material Design 3:** Modern color palettes and rounded layouts with fluid touch feedback.
- **Safe Area Support:** 完美适配各类刘海屏与手势操作底栏。  
  **Safe Area Support:** Perfect fit for notched screens and gesture navigation bars.

### 📁 Management / 存储管理
- **Multi-Server History:** 记录多个常用服务器，支持下拉菜单快速切换与安全删除。  
  **Multi-Server History:** Save multiple servers with quick-switch dropdown and secure deletion.
- **Smart Sorting & Filtering:** 文件夹置顶、多维排序，以及按视频、图片、文档智能分类。  
  **Smart Sorting & Filtering:** Folders-first logic, multi-criteria sorting, and smart filtering for media/docs.
- **File Operations:** 支持上传、重命名、删除（带二次确认）等核心操作。  
  **File Operations:** Support for uploads, renaming, and deletion (with safety confirmation).

### 🎥 Media & Preview / 媒体与预览
- **External Players:** 支持调用 **nPlayer, VLC, MX Player** 播放 4K 高清流媒体。  
  **External Players:** Stream 4K media directly via **nPlayer, VLC, or MX Player**.
- **Rich Preview:** 内置图片幻灯片、PDF 阅读器以及带语法高亮的代码/文本预览。  
  **Rich Preview:** Built-in gallery, PDF reader, and syntax-highlighted code/text viewer.
- **Link Copying:** 一键获取并复制原始直链。  
  **Link Copying:** One-tap to copy direct download links.

---

## 🛠️ Tech Stack / 技术栈

- **Core:** [React 19](https://react.dev/)
- **Styling:** [Tailwind CSS](https://tailwindcss.com/)
- **Icons:** [Lucide React](https://lucide.dev/)
- **API:** [AList V3 API](https://alist.nn.ci/)

---

## 🚀 Getting Started / 快速开始

1. **Deployment:** 该项目为单页面应用，可部署于 Vercel, Netlify 或 AList 自带的 `dist` 目录。
2. **Access:** 在手机浏览器中输入部署地址。
3. **Connect:** 输入您的 AList URL（包含端口，如 `5244`）、账号及密码。
4. **Enjoy:** 畅享您的私有云盘。

---

## 🛡️ Security & CORS / 安全与跨域

- **HTTPS Required:** 若本项目部署在 HTTPS 域名下，您的 AList 服务器也 **必须** 使用 HTTPS。  
  **HTTPS Required:** If this app is on HTTPS, your AList server **must** also be on HTTPS.
- **CORS Configuration:** 请确保 AList 后台的 `Allow Origins` 设置包含本应用的域名，或者设为 `*`。  
  **CORS:** Ensure `Allow Origins` in AList settings includes your domain or is set to `*`.

---

## 📄 License

This project is licensed under the MIT License.  
本项目采用 MIT 协议开源。

---
*Developed with ❤️ for the AList Community.*