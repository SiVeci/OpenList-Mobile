declare module 'react-native-vector-icons/MaterialCommunityIcons' {
  import { ComponentType } from 'react';
  interface IconProps {
    name: string;
    size: number;
    color?: string;
    style?: any;
  }
  const Icon: ComponentType<IconProps>;
  export default Icon;
}
