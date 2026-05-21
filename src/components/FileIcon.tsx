import React from 'react';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface Props {
  isDir: boolean;
  name: string;
  size?: number;
  color?: string;
}

const FILE_TYPE_MAP: Record<string, { icon: string; color: string }> = {
  mp4: { icon: 'file-video', color: '#ef4444' },
  mkv: { icon: 'file-video', color: '#ef4444' },
  avi: { icon: 'file-video', color: '#ef4444' },
  mov: { icon: 'file-video', color: '#ef4444' },
  flv: { icon: 'file-video', color: '#ef4444' },
  wmv: { icon: 'file-video', color: '#ef4444' },
  rmvb: { icon: 'file-video', color: '#ef4444' },
  mp3: { icon: 'file-music', color: '#a855f7' },
  flac: { icon: 'file-music', color: '#a855f7' },
  wav: { icon: 'file-music', color: '#a855f7' },
  ogg: { icon: 'file-music', color: '#a855f7' },
  m4a: { icon: 'file-music', color: '#a855f7' },
  jpg: { icon: 'file-image', color: '#22c55e' },
  jpeg: { icon: 'file-image', color: '#22c55e' },
  png: { icon: 'file-image', color: '#22c55e' },
  gif: { icon: 'file-image', color: '#22c55e' },
  webp: { icon: 'file-image', color: '#22c55e' },
  svg: { icon: 'file-image', color: '#22c55e' },
  pdf: { icon: 'file-pdf-box', color: '#3b82f6' },
  doc: { icon: 'file-word-box', color: '#3b82f6' },
  docx: { icon: 'file-word-box', color: '#3b82f6' },
  xls: { icon: 'file-excel-box', color: '#22c55e' },
  xlsx: { icon: 'file-excel-box', color: '#22c55e' },
  txt: { icon: 'file-document', color: '#3b82f6' },
  md: { icon: 'file-document', color: '#3b82f6' },
  zip: { icon: 'zip-box', color: '#ca8a04' },
  rar: { icon: 'zip-box', color: '#ca8a04' },
  '7z': { icon: 'zip-box', color: '#ca8a04' },
  tar: { icon: 'zip-box', color: '#ca8a04' },
  gz: { icon: 'zip-box', color: '#ca8a04' },
  js: { icon: 'language-javascript', color: '#666' },
  ts: { icon: 'language-typescript', color: '#666' },
  tsx: { icon: 'language-typescript', color: '#666' },
  py: { icon: 'language-python', color: '#666' },
  html: { icon: 'language-html5', color: '#666' },
  css: { icon: 'language-css3', color: '#666' },
  json: { icon: 'code-json', color: '#666' },
};

const FileIcon: React.FC<Props> = ({ isDir, name, size = 32, color }) => {
  if (isDir) {
    return (
      <Icon name="folder" size={size} color={color || '#818cf8'} />
    );
  }

  const ext = name.split('.').pop()?.toLowerCase() || '';
  const typeInfo = FILE_TYPE_MAP[ext];

  if (typeInfo) {
    return <Icon name={typeInfo.icon} size={size} color={color || typeInfo.color} />;
  }

  return <Icon name="file" size={size} color={color || '#9ca3af'} />;
};

export default FileIcon;
